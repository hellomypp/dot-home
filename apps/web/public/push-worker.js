self.addEventListener("push", (event) => {
  event.waitUntil(self.registration.showNotification("도트홈", {
    body: "가족의 새 소식이 있어요.",
    icon: "/art/app-icon.svg",
    tag: "dot-home-family",
  }));
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  event.waitUntil(self.clients.openWindow("/homes"));
});
