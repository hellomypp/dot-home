package kr.dothome;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev","test"})
class SocketIntegrationTest {
    @Autowired Environment env;@Autowired ObjectMapper json;@Autowired HomeService homes;@Autowired SocketRegistry registry;
    record Client(HttpClient http,String user,String csrf) implements AutoCloseable {public void close(){http.close();}}
    String base(){return "http://127.0.0.1:"+env.getProperty("local.server.port");}
    Client login()throws Exception{var http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();String token=token(http);var response=http.send(HttpRequest.newBuilder(URI.create(base()+"/api/auth/dev")).header("Content-Type","application/json").header("X-CSRF-TOKEN",token).POST(HttpRequest.BodyPublishers.ofString("{\"accountKey\":\"ws-"+UUID.randomUUID()+"\",\"name\":\"테스트\"}")).build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode());return new Client(http,json.readTree(response.body()).get("user").get("id").asText(),token(http));}
    String token(HttpClient client)throws Exception{return json.readTree(client.send(HttpRequest.newBuilder(URI.create(base()+"/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString()).body()).get("token").asText();}
    static class Listener implements WebSocket.Listener {final BlockingQueue<String> frames=new LinkedBlockingQueue<>();final StringBuilder buffer=new StringBuilder();public void onOpen(WebSocket socket){socket.request(1);}public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last){buffer.append(data);if(last){frames.add(buffer.toString());buffer.setLength(0);}socket.request(1);return null;}public CompletionStage<?> onClose(WebSocket socket,int code,String reason){frames.add("CLOSED "+code);return null;}}
    record Socket(WebSocket ws,Listener listener) implements AutoCloseable {public void close(){ws.abort();}void send(String value){ws.sendText(value+"\u0000",true).join();}String take(String contains)throws Exception{var received=new java.util.ArrayList<String>();long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(System.nanoTime()<until){String frame=listener.frames.poll(1,TimeUnit.SECONDS);if(frame!=null){received.add(frame);if(frame.contains(contains))return frame;}}throw new AssertionError("Expected frame: "+contains+" received="+received);}}
    Socket socket(Client client)throws Exception{var listener=new Listener();var ws=client.http.newWebSocketBuilder().header("Origin","http://localhost:5173").buildAsync(URI.create(base().replace("http:","ws:")+"/ws"),listener).get(5,TimeUnit.SECONDS);var socket=new Socket(ws,listener);socket.send("CONNECT\naccept-version:1.2\nhost:localhost\nX-CSRF-TOKEN:"+client.csrf+"\n\n");socket.take("CONNECTED");return socket;}
    void subscribe(Socket socket,String id)throws Exception{socket.send("SUBSCRIBE\nid:sync\ndestination:/user/queue/sync\n\n");socket.send("SUBSCRIBE\nid:world\ndestination:/topic/households/"+id+"/events\n\n");socket.send("SEND\ndestination:/app/households/"+id+"/sync\n\n{}");socket.take("SYNC_READY");}
    @Test void committedEventIsDeliveredAndUnauthorizedSubscribeIsRejected()throws Exception{
        try(var owner=login();var outsider=login()){var home=homes.create(owner.user,new Api.CreateHouse("실시간 집",2,"가온","sage",List.of()));String id=home.household().id();try(var allowed=socket(owner)){subscribe(allowed,id);homes.event(id,owner.user,new Api.CreateEvent(UUID.randomUUID().toString(),"NOTE","함께하는 하루"));assertTrue(allowed.take("LIFE_EVENT_CREATED").contains("함께하는 하루"));try(var denied=socket(outsider)){denied.send("SUBSCRIBE\nid:bad\ndestination:/topic/households/"+id+"/events\n\n");denied.take("CLOSED");}}}
    }
    @Test void topicPublishAndForgedActorAreRejected()throws Exception{
        try(var owner=login()){var home=homes.create(owner.user,new Api.CreateHouse("보안 집",1,"가온","sage",List.of()));String id=home.household().id();try(var socket=socket(owner)){socket.send("SEND\ndestination:/topic/households/"+id+"/events\n\n{}");socket.take("CLOSED");}
            try(var socket=socket(owner)){socket.send("SUBSCRIBE\nid:errors\ndestination:/user/queue/errors\n\n");subscribe(socket,id);socket.send("SEND\ndestination:/app/households/"+id+"/actions\ncontent-type:application/json\n\n{\"requestId\":\"forged\",\"kind\":\"MOVE_INTENT\",\"scene\":\"home\",\"x\":320,\"y\":320,\"memberId\":\"other\"}");socket.take("ACTION_DENIED");assertEquals(288,homes.world(id,owner.user).members().getFirst().y());}
        }
    }
    @Test void latestTabControlsCharacterAndLogoutClosesSockets()throws Exception{
        try(var owner=login()){var home=homes.create(owner.user,new Api.CreateHouse("탭 검증",1,"가온","sage",List.of()));String id=home.household().id();try(var first=socket(owner);var second=socket(owner)){subscribe(first,id);subscribe(second,id);first.send("SEND\ndestination:/app/households/"+id+"/actions\ncontent-type:application/json\n\n{\"requestId\":\"old-tab\",\"kind\":\"MOVE_INTENT\",\"scene\":\"home\",\"x\":320,\"y\":320}");first.take("CLOSED");var response=owner.http.send(HttpRequest.newBuilder(URI.create(base()+"/api/logout")).header("X-CSRF-TOKEN",owner.csrf).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());assertEquals(204,response.statusCode());second.take("CLOSED");}}
    }
    @Test void reconnectSnapshotRecoversMissedEventsAndRevokedMembershipClosesSocket()throws Exception{
        try(var owner=login();var guest=login()){String id=homes.create(owner.user,new Api.CreateHouse("복구의 집",2,"가온","sage",List.of())).household().id();var invite=homes.invite(id,owner.user);homes.accept(invite.token(),guest.user,new Api.Accept("나래","blue"));try(var first=socket(guest)){subscribe(first,id);}homes.event(id,owner.user,new Api.CreateEvent(UUID.randomUUID().toString(),"NOTE","연결 중단 중 남긴 소식"));try(var restored=socket(guest)){subscribe(restored,id);var snapshot=homes.world(id,guest.user);assertEquals("연결 중단 중 남긴 소식",snapshot.events().getFirst().message());var member=registry.members.findByHouseholdIdAndUserIdAndActiveTrue(id,guest.user).orElseThrow();member.active=false;registry.members.saveAndFlush(member);registry.expire();restored.take("CLOSED");assertThrows(ApiException.class,()->homes.world(id,guest.user));}}
    }
}
