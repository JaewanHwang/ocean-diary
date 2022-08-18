import { authApiInstance } from "@/api/index.js";
import { apiInstance } from "@/api/index.js";

async function getRoomList(token, category, lastRoomId, success, fail) {
  const authApi = authApiInstance(token);

  await authApi
    .get(`/api/rooms?category=${category}&lastRoomId=${lastRoomId}`)
    .then(success)
    .catch(fail);
}
async function createRoom(token, roomInfo, imageFile, success, fail) {
  const authApi = authApiInstance(token);
  const data = new FormData();
  data.append(
    "form",
    new Blob([JSON.stringify(roomInfo)], { type: "application/json" })
  );
  data.append("file", imageFile);
  await authApi.post(`/api/rooms`, data).then(success).catch(fail);
}
async function joinRoom(token, roomId, password, success, fail) {
  const authApi = authApiInstance(token);
  await authApi
    .post(`/api/rooms/${roomId}`, JSON.stringify({ pw: password }))
    .then(success)
    .catch(fail);
}
async function getImageFile(token, imageId, success, fail) {
  const authApi = authApiInstance(token);
  await authApi.get(`/api/image/${imageId}`).then(success).catch(fail);
}

async function leaveRoom(token, roomId, participantId, success, fail) {
  const authApi = authApiInstance(token);
  await authApi
    .delete(`/api/rooms/${roomId}/participants/${participantId}`)
    .then(success)
    .catch(fail);
}

async function GetUserInfo(roomId, success, fail) {
  const api = apiInstance();
  await api.get(`/api/rooms/${roomId}/detail`).then(success).catch(fail);
}

async function GetStamp(token, enterTime, exitTime, category, success, fail) {
  const api = authApiInstance(token);
  const data = {
    enterTime: enterTime,
    exitTime: exitTime,
    category: category,
  };
  await api
    .post(`/api/diary/stamp`, JSON.stringify(data))
    .then(success)
    .catch(fail);
}

export {
  getRoomList,
  createRoom,
  joinRoom,
  leaveRoom,
  GetUserInfo,
  getImageFile,
  GetStamp,
};
