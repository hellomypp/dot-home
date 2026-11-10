const houseId = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;

self.addEventListener("push", (event) => {
  let data;
  try { data = event.data?.json(); } catch { /* A legacy push uses the generic notice. */ }
  const householdId = typeof data?.householdId === "string" && houseId.test(data.householdId) ? data.householdId : null;
  const preview = typeof data?.preview === "string" && data.preview.length <= 100 ? data.preview : null;
  event.waitUntil(self.registration.showNotification("도트홈", {
    body: householdId && preview ? preview : "가족의 새 소식이 있어요.",
    icon: "/art/app-icon.svg",
    tag: householdId ? `dot-home-family-${householdId}` : "dot-home-family",
    data: { householdId },
  }));
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const id = event.notification.data?.householdId;
  const path = typeof id === "string" && houseId.test(id) ? `/home/${id}?notifications=1` : "/homes";
  event.waitUntil(self.clients.openWindow(path));
});
