package kr.dothome;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.*;
import org.springframework.messaging.*;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.simp.config.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.web.csrf.*;
import org.springframework.session.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
class SocketRegistry {
    final Map<String,WebSocketSession> connections=new ConcurrentHashMap<>();
    final Map<String,String> controllers=new ConcurrentHashMap<>();
    final Map<String,Set<String>> scopes=new ConcurrentHashMap<>();
    final SessionRepository<? extends Session> sessions;
    final Members members;
    SocketRegistry(SessionRepository<? extends Session> sessions,Members members){this.sessions=sessions;this.members=members;}
    boolean alive(Map<String,Object> attrs) {return attrs!=null && attrs.get("httpSession") instanceof String id && sessions.findById(id)!=null;}
    void register(WebSocketSession session){connections.put(session.getId(),session);}
    void forget(String id){connections.remove(id);scopes.remove(id);controllers.entrySet().removeIf(e->e.getValue().equals(id));}
    void close(WebSocketSession session){try{session.close(CloseStatus.POLICY_VIOLATION);}catch(Exception ignored){}finally{forget(session.getId());}}
    void closeHttpSession(String id){connections.values().stream().filter(s->id.equals(s.getAttributes().get("httpSession"))).toList().forEach(this::close);}
    synchronized void subscribe(String sessionId,String house,String user){
        if(!connections.containsKey(sessionId)||members.findByHouseholdIdAndUserIdAndActiveTrue(house,user).isEmpty())
            throw new org.springframework.security.access.AccessDeniedException("Membership ended");
        scopes.computeIfAbsent(sessionId,k->ConcurrentHashMap.newKeySet()).add(house);
        controllers.put(house+":"+user,sessionId);
    }
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    void revoke(CommittedMembershipExit event){
        List<WebSocketSession> affected;
        synchronized(this){
            affected=connections.values().stream().filter(session->session.getPrincipal()!=null
                &&event.userId().equals(session.getPrincipal().getName())
                &&scopes.getOrDefault(session.getId(),Set.of()).contains(event.householdId())).toList();
            affected.forEach(session->forget(session.getId()));
        }
        affected.forEach(this::close);
    }
    @Scheduled(fixedDelay=10_000) void expire(){for(WebSocketSession s:connections.values()){boolean revoked=scopes.getOrDefault(s.getId(),Set.of()).stream().anyMatch(h->s.getPrincipal()==null||members.findByHouseholdIdAndUserIdAndActiveTrue(h,s.getPrincipal().getName()).isEmpty());if(!alive(s.getAttributes())||revoked)close(s);}}
}

@Configuration @EnableWebSocketMessageBroker @EnableScheduling
class SocketConfiguration implements WebSocketMessageBrokerConfigurer {
    final SocketRegistry registry;final HomeService homes;final CsrfTokenRepository csrf;final String origin;
    SocketConfiguration(SocketRegistry registry,HomeService homes,CsrfTokenRepository csrf,@Value("${app.origin}") String origin){this.registry=registry;this.homes=homes;this.csrf=csrf;this.origin=origin;}
    @Bean ThreadPoolTaskScheduler brokerScheduler(){var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("stomp-heartbeat-");return scheduler;}
    @Override public void configureMessageBroker(MessageBrokerRegistry config){config.enableSimpleBroker("/topic","/queue").setHeartbeatValue(new long[]{10_000,10_000}).setTaskScheduler(brokerScheduler());config.setApplicationDestinationPrefixes("/app");config.setUserDestinationPrefix("/user");}
    @Override public void registerStompEndpoints(StompEndpointRegistry endpoints){endpoints.setPreserveReceiveOrder(true);endpoints.addEndpoint("/ws").setAllowedOrigins(origin).addInterceptors(new HandshakeInterceptor(){
        public boolean beforeHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Map<String,Object> attrs){if(!(request instanceof ServletServerHttpRequest servlet))return false;var req=servlet.getServletRequest();var session=req.getSession(false);if(session==null||req.getUserPrincipal()==null)return false;var token=csrf.loadToken(req);if(token==null)return false;attrs.put("httpSession",session.getId());attrs.put("csrf",token.getToken());return true;}
        public void afterHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Exception exception){}
    });}
    @Override public void configureWebSocketTransport(WebSocketTransportRegistration transport){transport.setMessageSizeLimit(8*1024).setSendBufferSizeLimit(64*1024).addDecoratorFactory(handler->new WebSocketHandlerDecorator(handler){
        @Override public void afterConnectionEstablished(WebSocketSession session)throws Exception{registry.register(session);super.afterConnectionEstablished(session);}
        @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status)throws Exception{registry.forget(session.getId());super.afterConnectionClosed(session,status);}
    });}
    @Override public void configureClientInboundChannel(ChannelRegistration registration){registration.interceptors(new ChannelInterceptor(){
        @Override public Message<?> preSend(Message<?> message,MessageChannel channel){
            var h=MessageHeaderAccessor.getAccessor(message,StompHeaderAccessor.class);if(h==null||h.getCommand()==null)return message;
            try {
            if(h.getCommand()==StompCommand.DISCONNECT)return message;
            if(h.getUser()==null||!registry.alive(h.getSessionAttributes()))throw new org.springframework.security.access.AccessDeniedException("Session expired");
            if(h.getCommand()==StompCommand.CONNECT){String actual=h.getFirstNativeHeader("X-CSRF-TOKEN"),expected=Objects.toString(h.getSessionAttributes().get("csrf"),"");if(actual==null||!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),actual.getBytes(StandardCharsets.UTF_8)))throw new org.springframework.security.access.AccessDeniedException("Invalid CSRF token");return message;}
            if(h.getCommand()==StompCommand.SEND||h.getCommand()==StompCommand.SUBSCRIBE){String destination=Objects.toString(h.getDestination(),"");if(h.getCommand()==StompCommand.SUBSCRIBE&&Set.of("/user/queue/errors","/user/queue/sync").contains(destination))return message;
                String pattern=h.getCommand()==StompCommand.SEND?"/app/households/([a-f0-9-]{36})/(?:actions|sync)":"/topic/households/([a-f0-9-]{36})/events";var matcher=java.util.regex.Pattern.compile(pattern).matcher(destination);if(!matcher.matches())throw new org.springframework.security.access.AccessDeniedException("Destination denied");String householdId=matcher.group(1);
                if(h.getCommand()==StompCommand.SUBSCRIBE)registry.subscribe(h.getSessionId(),householdId,h.getUser().getName());
                else {
                    homes.requireMember(householdId,h.getUser().getName());
                    String control=householdId+":"+h.getUser().getName();
                    if(destination.endsWith("/actions")&&!h.getSessionId().equals(registry.controllers.get(control)))
                        throw new org.springframework.security.access.AccessDeniedException("Another tab controls this character");
                }
            }else if(h.getCommand()!=StompCommand.UNSUBSCRIBE)throw new org.springframework.security.access.AccessDeniedException("Unsupported command");
            return message;
            } catch(RuntimeException denied){var connection=registry.connections.get(h.getSessionId());if(connection!=null)registry.close(connection);return null;}
        }
    });}
}

@Service
class MovementService {
    final HomeService homes;final Map<String,Long> rate=new ConcurrentHashMap<>();final Map<String,LinkedHashSet<String>> requests=new ConcurrentHashMap<>();
    MovementService(HomeService homes){this.homes=homes;}
    static final double[][] OTHER={{112,200,120,48},{410,200,110,48}};
    static boolean walkable(double x,double y,String scene){if(scene.equals("home"))return Spaces.walkable(x,y,"garden_home");if(!Double.isFinite(x)||!Double.isFinite(y)||x<51||x>589||y<137||y>399)return false;for(double[] r:OTHER)if(x>r[0]-7&&x<r[0]+r[2]+7&&y>r[1]-7&&y<r[1]+r[3]+7)return false;return true;}
    @Transactional void apply(String householdId,String userId,Api.Intent intent){
        Household house=homes.lock(householdId,userId);Member member=homes.requireMember(householdId,userId);if(!member.place.equals(intent.scene()))throw ApiException.invalid("내 캐릭터가 있는 공간에서만 움직일 수 있어요.");
        if("office".equals(member.place)||"school".equals(member.place))
            throw ApiException.invalid("이 장면에서는 캐릭터를 직접 움직일 수 없어요.");
        var seen=requests.computeIfAbsent(member.id,k->new LinkedHashSet<>());synchronized(seen){if(seen.contains(intent.requestId()))return;}
        long now=System.currentTimeMillis();Long previous=rate.put(member.id,now);if(previous!=null&&now-previous<120)throw ApiException.invalid("이동 요청이 너무 빨라요.");
        if(intent.kind().equals("MOVE_INTENT")){if(intent.x()==null||intent.y()==null||!(intent.scene().equals("home")?Spaces.walkable(intent.x(),intent.y(),house.presetId):walkable(intent.x(),intent.y(),intent.scene())))throw ApiException.invalid("이곳으로 이동할 수 없어요.");member.x=intent.x();member.y=intent.y();member.avatarSpace="v62";member.action="walk";}
        else{if(!Set.of("sofa","fridge","table","window","calendar","entrance","pet_rest").contains(Objects.toString(intent.target(),"")))throw ApiException.invalid("알 수 없는 가구예요.");member.action="idle";}
        member.avatarUpdatedAt=Instant.now();synchronized(seen){seen.add(intent.requestId());while(seen.size()>256)seen.remove(seen.iterator().next());}homes.emit(house,"AVATAR_ACTION_CHANGED",homes.memberView(member,house.presetId));
    }
}

@Controller
class WorldController {
    final MovementService movement;final SimpMessagingTemplate messaging;final HomeService homes;
    WorldController(MovementService movement,SimpMessagingTemplate messaging,HomeService homes){this.movement=movement;this.messaging=messaging;this.homes=homes;}
    @MessageMapping("/households/{id}/sync") @org.springframework.messaging.simp.annotation.SendToUser(value="/queue/sync",broadcast=false)
    Map<String,String> sync(@DestinationVariable String id,Principal user){homes.requireMember(id,user.getName());return Map.of("type","SYNC_READY");}
    @MessageMapping("/households/{id}/actions") void intent(@DestinationVariable String id,Principal user,@jakarta.validation.Valid @Payload Api.Intent intent){movement.apply(id,user.getName(),intent);}
    @MessageExceptionHandler void error(Exception exception,Principal user){if(user!=null)messaging.convertAndSendToUser(user.getName(),"/queue/errors",Map.of("code","ACTION_DENIED","message",exception instanceof ApiException?exception.getMessage():"이동 요청을 처리하지 못했어요."));}
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT) public void broadcast(CommittedSignal signal){messaging.convertAndSend("/topic/households/"+signal.householdId()+"/events",signal.signal());}
}
