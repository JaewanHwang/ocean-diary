<template>
  <div class="background" ref="bg">
    <router-view></router-view>
  </div>
  <DiaryNav></DiaryNav>
</template>

<script>
import { onMounted, ref } from "vue";
import { roomImages, stations, indexes } from "@/const/const.js";
import DiaryNav from "@/components/diary/DiaryIcon.vue";

export default {
  components: {
    DiaryNav,
  },
  setup() {
    const urlParams = new URL(location.href).searchParams;
    const dest = urlParams.get("dest");
    // dest에 해당하는 index를 찾는다.
    var index = indexes[dest];
    // index로 배경화면을 바꿔준다.
    const bg = ref(null);
    onMounted(() => {
      const url = require(`@/assets/${roomImages[index]}`);
      bg.value.style = `background-image: url(${url});`;
    });
    return {
      index,
      stations,
      bg,
    };
  },
};
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
.banner {
  height: 60vh;
  display: flex;
  justify-content: center;
  align-items: center;
}

.ticket {
  width: 172px;
  height: 114px;
  position: relative;
  left: 40vw;
  bottom: 20vh;
}
</style>
