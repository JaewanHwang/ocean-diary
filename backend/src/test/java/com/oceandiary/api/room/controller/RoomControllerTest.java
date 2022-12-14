package com.oceandiary.api.room.controller;

import com.oceandiary.MvcTest;
import com.oceandiary.api.common.category.Category;
import com.oceandiary.api.file.entity.Image;
import com.oceandiary.api.room.dto.RoomRequest;
import com.oceandiary.api.room.dto.RoomResponse;
import com.oceandiary.api.room.entity.Room;
import com.oceandiary.api.room.service.RoomService;
import com.oceandiary.api.user.domain.Role;
import com.oceandiary.api.user.domain.SocialProvider;
import com.oceandiary.api.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Room API ?????????")
@WebMvcTest(RoomController.class)
class RoomControllerTest extends MvcTest {

    @MockBean
    private RoomService roomService;
    private User user;
    private Room room1;
    private Room room2;
    private List<Room> roomList = new ArrayList<>();

    @BeforeEach
    public void setup() {
        user = User.builder()
                .id(1L)
                .oauthId("1")
                .name("?????????")
                .birth(LocalDate.of(2002, 8,8))
                .provider(SocialProvider.NAVER)
                .role(Role.USER)
                .visitedAt(LocalDateTime.now())
                .email("oceandiary@oceandiary.com")
                .build();
        room1 = Room.builder()
                .id(1L)
                .title("??????")
                .rule("??????")
                .category(Category.OCEAN)
                .isOpen(false)
                .pw("1234")
                .user(user)
                .maxNum(6)
                .image(Image.builder()
                        .id(1L)
                        .name("uploadedFileName")
                        .originName("originalFileName")
                        .room(room1)
                        .extension("png")
                        .width(637)
                        .height(429)
                        .size(561417L)
                        .url("url").build())
                .build();
        room2 = Room.builder()
                .id(2L)
                .title("??????2")
                .rule("??????2")
                .category(Category.FESTIVAL)
                .isOpen(true)
                .pw(null)
                .user(user)
                .maxNum(6)
                .image(null)
                .build();
        room1.setCreatedAt(LocalDateTime.of(2022,8,8,9,00));
        room2.setCreatedAt(LocalDateTime.of(2022,8,11,11,00));
        roomList.add(room1);
        roomList.add(room2);
    }

    @Test
    @DisplayName("???_??????")
    void createRoom() throws Exception {
        RoomRequest.CreateRoom request = RoomRequest.CreateRoom.builder()
                .pw("1234")
                .title("??????")
                .isOpen(false)
                .maxNum(6)
                .rule("??????")
                .categoryId(Category.OCEAN)
                .build();

        RoomResponse.CreateRoom response = RoomResponse.CreateRoom.builder()
                .roomId(1L)
                .participantId(1L)
                .token("openvidu-token")
                .connectionId("connection-id")
                .build();

        String jsonRequest = objectMapper.writeValueAsString(request);
        MockMultipartFile form = new MockMultipartFile("form", "form", "application/json", jsonRequest.getBytes(StandardCharsets.UTF_8));
        InputStream inputStream = new ClassPathResource("dummy/image/image.png").getInputStream();
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", inputStream.readAllBytes());

        given(roomService.createRoom(any(), any(), any())).willReturn(response);
        ResultActions results = mvc.perform(multipart("/api/rooms")
                .file(form)
                .file(file)
                .contentType(MediaType.MULTIPART_MIXED)
                .accept(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
        );

        results.andExpect(status().isOk())
                .andDo(print())
                .andDo(document("session-create-room",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestParts(
                                partWithName("form").description("??? ?????? ?????? - JSON"),
                                partWithName("file").description("????????? ??????")
                        ),
                        responseFields(
                                fieldWithPath("roomId").type(JsonFieldType.NUMBER).description("??? ?????????"),
                                fieldWithPath("participantId").type(JsonFieldType.NUMBER).description("????????? ?????????"),
                                fieldWithPath("token").type(JsonFieldType.STRING).description("OpenVidu Token"),
                                fieldWithPath("connectionId").type(JsonFieldType.STRING).description("Connection ????????? ?????????")
                        )
                ));
        verify(roomService).createRoom(any(RoomRequest.CreateRoom.class), any(MultipartFile.class), any(User.class));
    }

    @Test
    @DisplayName("???_??????")
    void enterRoom() throws Exception {
        RoomRequest.EnterRoom request = RoomRequest.EnterRoom.builder()
                .pw("1234")
                .build();

        RoomResponse.EnterRoom response = RoomResponse.EnterRoom.builder()
                .participantId(1L)
                .token("openvidu-token")
                .connectionId("connection-id")
                .build();

        given(roomService.enterRoom(any(), any(), any(), any())).willReturn(response);
        ResultActions results = mvc.perform(
                        RestDocumentationRequestBuilders.post("/api/rooms/{roomId}", 1L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .characterEncoding("UTF-8")
        );

        results.andExpect(status().isOk())
                .andDo(print())
                .andDo(document("session-enter-room",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("roomId").description("??? ?????????")
                        ),
                        requestFields(
                                fieldWithPath("pw").type(JsonFieldType.STRING).description("????????????")
                        ),
                        responseFields(
                                fieldWithPath("participantId").type(JsonFieldType.NUMBER).description("????????? ?????????"),
                                fieldWithPath("token").type(JsonFieldType.STRING).description("OpenVidu Token"),
                                fieldWithPath("connectionId").type(JsonFieldType.STRING).description("Connection ????????? ?????????")
                        )
                ));
        verify(roomService).enterRoom(any(RoomRequest.EnterRoom.class), anyLong(), any(User.class), any());
    }

    @Test
    @DisplayName("???_??????")
    void exitRoom() throws Exception{
        RoomResponse.OnlyId response = RoomResponse.OnlyId.builder().roomId(1L).build();
        given(roomService.exitRoom(any(), any(), any())).willReturn(response);

        ResultActions results = mvc.perform(
                RestDocumentationRequestBuilders.delete("/api/rooms/{roomId}/participants/{participantId}", 1L, 1L)
        );

        results.andExpect(status().isOk())
                .andDo(print())
                .andDo(document("session-exit-room",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("roomId").description("??? ?????????"),
                                parameterWithName("participantId").description("????????? ?????????")
                        ),
                        responseFields(
                                fieldWithPath("roomId").type(JsonFieldType.NUMBER).description("??? ?????????")
                        )
                ));
        verify(roomService).exitRoom(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("???_??????_??????")
    void searchByCondition() throws Exception {
        Page<Room> roomPage = new PageImpl<>(roomList, PageRequest.of(1, 5), roomList.size());
        Page<RoomResponse.SearchRooms> response = roomPage.map(room1 -> RoomResponse.SearchRooms.build(room1, 3));
        given(roomService.search(any(), any())).willReturn(response);

        ResultActions results = mvc.perform(get("/api/rooms"));

        results.andExpect(status().isOk())
                .andDo(print())
                .andDo(document("search-rooms",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("content[].roomId").type(JsonFieldType.NUMBER).description("??? ?????????"),
                                fieldWithPath("content[].title").type(JsonFieldType.STRING).description("??????"),
                                fieldWithPath("content[].createdBy").type(JsonFieldType.NUMBER).description("?????? ?????????"),
                                fieldWithPath("content[].imageId").type(JsonFieldType.NUMBER).description("????????? ?????????").optional(),
                                fieldWithPath("content[].maxNum").type(JsonFieldType.NUMBER).description("?????? ?????? ??????"),
                                fieldWithPath("content[].curNum").type(JsonFieldType.NUMBER).description("?????? ?????? ??????"),
                                fieldWithPath("content[].isOpen").type(JsonFieldType.BOOLEAN).description("??? ?????? ?????? - ?????? = true"),
                                fieldWithPath("totalElements").description("?????? ??????"),
                                fieldWithPath("last").description("????????? ??????????????? ??????"),
                                fieldWithPath("totalPages").description("?????? ?????????")
                        )
                ));
        verify(roomService).search(any(), any());
    }

    @Test
    @DisplayName("???_??????_??????")
    void roomInfo() throws Exception {
        RoomResponse.RoomInfo response = RoomResponse.RoomInfo.builder().roomId(1L).sessionId("sessionId").categoryId(Category.FESTIVAL).createdBy(1L).title("??????").rule("??????").curNum(2).maxNum(4).imageId(1L).isOpen(true).build();
        given(roomService.getRoomInfo(any())).willReturn(response);
        ResultActions results = mvc.perform(RestDocumentationRequestBuilders.get("/api/rooms/{roomId}/info", 1L));

        results.andExpect(status().isOk())
                .andDo(print())
                .andDo(document("room-info",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("roomId").description("??? ?????????")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("roomId").type(JsonFieldType.NUMBER).description("??? ?????????"),
                                fieldWithPath("sessionId").type(JsonFieldType.STRING).description("?????? ?????????"),
                                fieldWithPath("title").type(JsonFieldType.STRING).description("??????"),
                                fieldWithPath("categoryId").type(JsonFieldType.STRING).description("???????????? ????????? - {OCEAN, LIBRARY, CAFE, FESTIVAL, HOME}"),
                                fieldWithPath("createdBy").type(JsonFieldType.NUMBER).description("?????? ?????????"),
                                fieldWithPath("imageId").type(JsonFieldType.NUMBER).description("????????? ?????????").optional(),
                                fieldWithPath("maxNum").type(JsonFieldType.NUMBER).description("?????? ?????? ??????"),
                                fieldWithPath("curNum").type(JsonFieldType.NUMBER).description("?????? ?????? ??????"),
                                fieldWithPath("isOpen").type(JsonFieldType.BOOLEAN).description("??? ?????? ?????? - ?????? = true")
                        )
                ));
        verify(roomService).getRoomInfo(any());
    }

    @Test
    @DisplayName("???_????????????_??????")
    void roomDetail() throws Exception {
        RoomResponse.RoomInfo roomInfo = RoomResponse.RoomInfo.builder().roomId(1L).sessionId("sessionId").categoryId(Category.FESTIVAL).createdBy(1L).title("??????").rule("??????").curNum(2).maxNum(4).imageId(1L).isOpen(true).build();
        List<RoomResponse.RoomDetail.ParticipantInfo> participantInfoList = new ArrayList<>();
        RoomResponse.RoomDetail.ParticipantInfo participantInfo1 = RoomResponse.RoomDetail.ParticipantInfo.builder()
                .participantId(1L)
                .name("?????????")
                .enterTime(LocalDateTime.now())
                .token("token")
                .connectionId("connectionId").build();
        RoomResponse.RoomDetail.ParticipantInfo participantInfo2 = RoomResponse.RoomDetail.ParticipantInfo.builder()
                .participantId(2L)
                .name("?????????")
                .enterTime(LocalDateTime.now())
                .token("token")
                .connectionId("connectionId").build();
        participantInfoList.add(participantInfo1);
        participantInfoList.add(participantInfo2);
        RoomResponse.RoomDetail response = RoomResponse.RoomDetail.builder().roomInfo(roomInfo).participantList(participantInfoList).build();
        given(roomService.getRoomDetail(any())).willReturn(response);

        ResultActions results = mvc.perform(RestDocumentationRequestBuilders.get("/api/rooms/{roomId}/detail", 1L));

        results.andExpect(status().isOk())
                .andDo(print())
                .andDo(document("room-detail",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("roomId").description("??? ?????????")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("roomInfo.roomId").type(JsonFieldType.NUMBER).description("??? ?????????"),
                                fieldWithPath("roomInfo.sessionId").type(JsonFieldType.STRING).description("?????? ?????????"),
                                fieldWithPath("roomInfo.title").type(JsonFieldType.STRING).description("??????"),
                                fieldWithPath("roomInfo.categoryId").type(JsonFieldType.STRING).description("???????????? ????????? - {OCEAN, LIBRARY, CAFE, FESTIVAL, HOME}"),
                                fieldWithPath("roomInfo.createdBy").type(JsonFieldType.NUMBER).description("?????? ?????????"),
                                fieldWithPath("roomInfo.imageId").type(JsonFieldType.NUMBER).description("????????? ?????????").optional(),
                                fieldWithPath("roomInfo.maxNum").type(JsonFieldType.NUMBER).description("?????? ?????? ??????"),
                                fieldWithPath("roomInfo.curNum").type(JsonFieldType.NUMBER).description("?????? ?????? ??????"),
                                fieldWithPath("roomInfo.isOpen").type(JsonFieldType.BOOLEAN).description("??? ?????? ?????? - ?????? = true"),
                                fieldWithPath("participantList[].participantId").type(JsonFieldType.NUMBER).description("??? ?????????"),
                                fieldWithPath("participantList[].name").type(JsonFieldType.STRING).description("????????? ??????"),
                                fieldWithPath("participantList[].enterTime").type(JsonFieldType.STRING).description("?????? ??????"),
                                fieldWithPath("participantList[].token").type(JsonFieldType.STRING).description("??????"),
                                fieldWithPath("participantList[].connectionId").type(JsonFieldType.STRING).description("connection ?????????")
                        )
                ));
        verify(roomService).getRoomDetail(any());
    }

    @Test
    @DisplayName("???_??????_??????")
    void updateRoomInfo() throws Exception {
        RoomRequest.UpdateRoom request = RoomRequest.UpdateRoom.builder()
                .title("??????")
                .isOpen(false)
                .maxNum(6)
                .rule("??????")
                .pw("1234")
                .build();

        RoomResponse.OnlyId response = RoomResponse.OnlyId.builder()
                .roomId(1L)
                .build();

        given(roomService.updateRoomInfo(any(), any(), any())).willReturn(response);
        ResultActions results = mvc.perform(patch("/api/rooms/{roomId}/info", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .characterEncoding("UTF-8")
        );

        results.andExpect(status().isOk())
                .andDo(print())
                .andDo(document("update-room-info",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("roomId").description("??? ?????????")
                        ),
                        requestFields(
                                fieldWithPath("title").type(JsonFieldType.STRING).description("?????????"),
                                fieldWithPath("rule").type(JsonFieldType.STRING).description("?????????"),
                                fieldWithPath("isOpen").type(JsonFieldType.BOOLEAN).description("????????????"),
                                fieldWithPath("pw").type(JsonFieldType.STRING).description("???????????????"),
                                fieldWithPath("maxNum").type(JsonFieldType.NUMBER).description("?????? ?????? ?????? (?????? ????????? ????????? ?????? ??? ??????)")
                        ),
                        responseFields(
                                fieldWithPath("roomId").type(JsonFieldType.NUMBER).description("??? ?????????")
                        )
                ));
        verify(roomService).updateRoomInfo(any(), any(), any());

    }
    @Test
    @DisplayName("???_?????????_??????")
    void updateRoomImage() throws Exception {
        RoomResponse.OnlyId response = RoomResponse.OnlyId.builder()
                .roomId(1L)
                .build();

        InputStream inputStream = new ClassPathResource("dummy/image/image.png").getInputStream();
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", inputStream.readAllBytes());

        given(roomService.updateRoomImage(any(), any(), any())).willReturn(response);
        ResultActions results = mvc.perform(multipart("/api/rooms/{roomId}/image", 1L)
                .file(file)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .characterEncoding("UTF-8")
        );

        results.andExpect(status().isOk())
                .andDo(print())
                .andDo(document("update-room-image",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestParts(
                                partWithName("file").description("????????? ??????").optional()
                        ),
                        responseFields(
                                fieldWithPath("roomId").type(JsonFieldType.NUMBER).description("??? ?????????")
                        )
                ));
        verify(roomService).updateRoomImage(any(), any(), any());

    }

    @Test
    @DisplayName("???_??????")
    void dropoutParticipant() throws Exception{
        RoomResponse.OnlyId response = RoomResponse.OnlyId.builder()
                .roomId(1L)
                .build();

        given(roomService.dropoutParticipant(any(), any(), any())).willReturn(response);
        ResultActions results = mvc.perform(
                RestDocumentationRequestBuilders.post("/api/rooms/{roomId}/participants/{participantId}", 1L, 1L)
        );

        results.andExpect(status().isOk())
                .andDo(print())
                .andDo(document("dropout-participant",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("roomId").description("??? ?????????"),
                                parameterWithName("participantId").description("????????? ?????????")
                        ),

                        responseFields(
                                fieldWithPath("roomId").type(JsonFieldType.NUMBER).description("??? ?????????")
                        )
                ));
        verify(roomService).dropoutParticipant(any(), any(), any());
    }

}