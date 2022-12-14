const userStore = {
  namespaced: true,
  state: {
    isLogin: false,
    social: "",
    token: "",
    oauthId: "",
    name: "",
    userId: Number,
  },
  getters: {
    checkUserInfo: function (state) {
      return state.userInfo;
    },
  },
  mutations: {
    SET_USERID: (state, userId) => {
      state.userId = userId;
    },
    SET_IS_LOGIN: (state, isLogin) => {
      state.isLogin = isLogin;
    },
    SET_SOCIAL: (state, social) => {
      state.social = social;
    },
    SET_TOKEN: (state, token) => {
      state.token = "Bearer " + token;
    },
    SET_OAUTH_ID: (state, oauthId) => {
      state.oauthId = oauthId;
    },
    SET_NAME: (state, name) => {
      state.name = name;
    },
    SET_INIT: (state) => {
      state.isLogin = false;
      state.social = "";
      state.token = "";
      state.oauthId = "";
      state.name = "";
      state.userId = Number;
    },
  },
  // actions: {
  //   async userConfirm({ commit }, user) {
  //     await login(
  //       user,
  //       (response) => {
  //         if (response.data.message === "success") {
  //           let token = response.data["access-token"];
  //           commit("SET_IS_LOGIN", true);
  //           commit("SET_IS_LOGIN_ERROR", false);
  //           sessionStorage.setItem("access-token", token);
  //         } else {
  //           commit("SET_IS_LOGIN", false);
  //           commit("SET_IS_LOGIN_ERROR", true);
  //         }
  //       },
  //       () => {}
  //     );
  //   },
  //   getUserInfo({ commit }, token) {
  //     let decode_token = jwt_decode(token);
  //     findById(
  //       decode_token.userId,
  //       (response) => {
  //         if (response.data.message === "success") {
  //           console.log(response.data.userInfoDto);
  //           commit("SET_USER_INFO", response.data.userInfoDto);
  //         } else {
  //           console.log("No Such User!");
  //         }
  //       },
  //       (error) => {
  //         console.log(error);
  //       }
  //     );
  //   },
  // },
};

export default userStore;
