import { createRouter, createWebHistory } from "vue-router";

const routes = [
  {
    path: "/",
    name: "main",
    component: () => import("@/views/MainView.vue"),
  },
  // 로그인 관련
  {
    path: "/login",
    name: "login",
    component: () => import("@/views/UserView.vue"),
    children: [
      {
        path: "menu",
        name: "login-menu",
        component: () => import("@/components/user/LoginMenu.vue"),
      },
      {
        path: "social",
        name: "login-social",
        component: () => import("@/components/user/LoginSocial.vue"),
      },
      {
        path: "single",
        name: "login-single",
        component: () => import("@/components/user/LoginSingle.vue"),
      },
      {
        path: "signup",
        name: "login-signup",
        component: () => import("@/components/user/LoginSignup.vue"),
      },
    ],
  },
  // oauth 관련
  {
    path: "/oauth2/naver",
    name: "NaverHandler",
    component: () => import("@/components/user/oauth/NaverHandler.vue"),
  },
  {
    path: "/oauth2/kakao",
    name: "KakaoHandler",
    component: () => import("@/components/user/oauth/KakaoHandler.vue"),
  },
  // station 관련
  {
    path: "/station",
    name: "station",
    component: () => import("@/views/StationView.vue"),
    children: [
      {
        path: "home",
        name: "station-chat",
        props: true,
        component: () => import("@/components/station/StationChat.vue"),
      },
      {
        path: "input",
        name: "station-input",
        component: () => import("@/components/station/StationInput.vue"),
      },
      {
        path: "map",
        name: "station-map",
        component: () => import("@/components/station/StationMap.vue"),
      },
    ],
  },
  // 이동화면 관련
  {
    path: "/train",
    name: "train",
    component: () => import("@/views/TrainView.vue"),
    children: [
      {
        path: "depart",
        name: "train-depart",
        component: () => import("@/components/train/TrainDepart.vue"),
      },
      {
        path: "move",
        name: "train-move",
        component: () => import("@/components/train/TrainMove.vue"),
      },
      {
        path: "arrive",
        name: "train-arrive",
        component: () => import("@/components/train/TrainArrive.vue"),
      },
      {
        path: "room",
        name: "library_study",
        component: () => import("@/components/room/StudyRoom.vue"),
      },
    ],
  },
  // 방 생성 관련,
  {
    path: "/lobby",
    name: "lobby",
    component: () => import("@/views/LobbyView.vue"),
    children: [
      {
        path: "list",
        name: "room-list",
        component: () => import("@/components/room/RoomList.vue"),
      },
      {
        path: "create",
        name: "room-create",
        component: () => import("@/components/room/RoomCreate.vue"),
      },
    ],
  },
  {
    path: "/festival",
    name: "festival",
    component: () => import("@/views/FestivalView.vue"),
    children: [
      {
        path: "study",
        name: "festival-room",
        component: () => import("@/components/room/StudyRoom.vue"),
      },
    ],
  },
  // {
  //   path: "/cafe",
  //   name: "cafe",
  //   component: () => import("@/views/CafeView.vue"),
  // },
  // {
  //   path: "/house",
  //   name: "house",
  //   component: () => import("@/views/HouseView.vue"),
  // },
  // {
  //   path: "/library",
  //   name: "library",
  //   component: () => import("@/views/LibraryView.vue"),
  // },
  // {
  //   path: "/ocean",
  //   name: "ocean",
  //   component: () => import("@/views/OceanView.vue"),
  // },
  // 다이어리 관련,
  {
    path: "/diary",
    name: "diary",
    component: () => import("@/views/DiaryView.vue"),
    children: [
      {
        path: "contents",
        name: "diary-contents",
        component: () => import("@/components/diary/DiaryContents.vue"),
      },
    ],
  },
];

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes,
});

export default router;
