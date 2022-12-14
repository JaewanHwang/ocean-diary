package com.oceandiary.api.room.service;

import com.oceandiary.api.common.exception.BusinessException;
import com.oceandiary.api.diary.dto.DiaryRequest;
import com.oceandiary.api.diary.entity.Stamp;
import com.oceandiary.api.diary.repository.StampRepository;
import com.oceandiary.api.file.entity.Image;
import com.oceandiary.api.file.repository.ImageRepository;
import com.oceandiary.api.file.service.ImageService;
import com.oceandiary.api.file.service.S3Service;
import com.oceandiary.api.room.dto.RoomRequest;
import com.oceandiary.api.room.dto.RoomResponse;
import com.oceandiary.api.room.entity.*;
import com.oceandiary.api.room.exception.PasswordNotValidException;
import com.oceandiary.api.room.repository.*;
import com.oceandiary.api.user.entity.User;
import io.openvidu.java.client.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class RoomService {
    // OpenVidu object as entrypoint of the SDK
    private final OpenVidu openVidu;
    // URL where our OpenVidu server is listening
    private final String OPENVIDU_URL;
    // Secret shared with our OpenVidu server
    private final String SECRET;
    private final S3Service s3Service;
    private final ImageService imageService;
    private final RoomRepository roomRepository;
    private final RoomOnRedisRepository roomOnRedisRepository;
    private final ParticipantRepository participantRepository;
    private final ParticipantOnRedisRepository participantOnRedisRepository;
    private final DropoutRepository dropoutRepository;
    private final ImageRepository imageRepository;
    private final StampRepository stampRepository;

    @Value("${openvidu.secret}")
    private String OPENVIDU_SECRET;

    public RoomService(@Value("${openvidu.secret}") String secret,
                       @Value("${openvidu.url}") String openviduUrl,
                       RoomRepository roomRepository,
                       RoomOnRedisRepository roomOnRedisRepository,
                       ParticipantRepository participantRepository,
                       ParticipantOnRedisRepository participantOnRedisRepository,
                       DropoutRepository dropoutRepository,
                       ImageRepository imageRepository,
                       S3Service s3Service,
                       ImageService imageService,
                       StampRepository stampRepository) {
        this.SECRET = secret;
        this.OPENVIDU_URL = openviduUrl;
        this.roomRepository = roomRepository;
        this.roomOnRedisRepository = roomOnRedisRepository;
        this.participantRepository = participantRepository;
        this.participantOnRedisRepository = participantOnRedisRepository;
        this.dropoutRepository = dropoutRepository;
        this.imageRepository = imageRepository;
        this.s3Service = s3Service;
        this.imageService = imageService;
        this.stampRepository = stampRepository;
        this.openVidu = new OpenVidu(OPENVIDU_URL, SECRET);
    }

    public RoomResponse.CreateRoom createRoom(RoomRequest.CreateRoom request, MultipartFile file, User user) {

        String serverData = "{\"userId\": \"" + user.getId() + "\"," +
                "\"name\": \"" + user.getName() + "\"}";
        ConnectionProperties connectionProperties =
                new ConnectionProperties.Builder().type(ConnectionType.WEBRTC).data(serverData).role(OpenViduRole.MODERATOR).build();

        log.info("OpenVidu connection successful");

        if (roomOnRedisRepository.findByCreatedBy(user.getId()).isPresent()) {
            throw new BusinessException("?????? ?????? ????????? ????????? ?????? ?????????");
        }

        try {
            Image newImage = null;
            // AWS S3 image upload
            if (file != null && !file.isEmpty()) {
                Long imageId = imageService.save(file).getId();
                newImage = imageRepository.findById(imageId).orElseThrow();
            }

            Room room = Room.builder()
                    .category(request.getCategoryId())
                    .user(user)
                    .title(request.getTitle())
                    .rule(request.getRule())
                    .image(newImage)
                    .maxNum(request.getMaxNum())
                    .isOpen(request.getIsOpen())
                    .pw(request.getPw())
                    .build();

            Room newRoom = roomRepository.save(room);
            log.info("Session created: {}", newRoom);

            // OpenVidu API: Create a new OpenVidu Session
            Session session = this.openVidu.createSession();
            // Generate a new Connection with the recently created connectionProperties
            Connection connection = session.createConnection(connectionProperties);
            String token = connection.getToken();

            RoomOnRedis newRoomOnRedis = RoomOnRedis.builder().id(newRoom.getId()).sessionId(session.getSessionId()).createdBy(user.getId()).build();
            roomOnRedisRepository.save(newRoomOnRedis);
            ParticipantOnRedis participantOnRedis = ParticipantOnRedis.builder()
                    .id(newRoom.getId())
                    .participantTokenMap(new ConcurrentHashMap<>())
                    .participantConnectionMap(new ConcurrentHashMap<>())
                    .build();
            ParticipantOnRedis newParticipantOnRedis = participantOnRedisRepository.save(participantOnRedis);

            Participant newParticipant = Participant.builder()
                    .room(newRoom)
                    .user(user)
                    .name(user.getName())
                    .enterDate(LocalDateTime.now())
                    .build();

            newParticipant = participantRepository.save(newParticipant);

            newParticipantOnRedis.addParticipant(newParticipant.getId(), token, connection.getConnectionId());
            participantOnRedisRepository.save(newParticipantOnRedis);

            return RoomResponse.CreateRoom.builder()
                    .roomId(newRoom.getId())
                    .participantId(newParticipant.getId())
                    .token(token)
                    .connectionId(connection.getConnectionId())
                    .build();

        } catch (Exception e) {
            throw new BusinessException("?????? ????????? ?????? ??????");
        }
    }

    /**
     * 1. ???????????? ????????? ???????????? ???????????? ????????????.
     * 2. ?????? ?????? curNum??? maxNum??? ???????????? ????????? ??? ??????.
     */
    public RoomResponse.EnterRoom enterRoom(RoomRequest.EnterRoom request, Long roomId, User user, String name) {

        String serverData = "{\"userId\": \"" + (user != null ? "" + user.getId().toString() : "null") + "\"," + "\"name\": \"" + (user != null ? user.getName() : name) + "\"}";
        ConnectionProperties connectionProperties =
                new ConnectionProperties.Builder().type(ConnectionType.WEBRTC).data(serverData).role(OpenViduRole.PUBLISHER).build();
        Room room = roomRepository.findById(roomId).orElseThrow();

        if (user != null && dropoutRepository.findByUser_idAndRoom_id(user.getId(), roomId).isPresent()) {
            throw new BusinessException("?????? ???????????? ???????????? ????????? ??? ????????????.");
        }

        if (participantOnRedisRepository.findById(roomId).orElseThrow().getParticipantTokenMap().size() >= room.getMaxNum()) {
            throw new BusinessException("?????? ?????? ?????? ?????? ?????????????????????.");
        }

        try {
            String sessionId = roomOnRedisRepository.findById(roomId).orElseThrow().getSessionId();
            Session session = getSession(sessionId);

            Connection connection = session.createConnection(connectionProperties);
            String token = connection.getToken();

            // ???????????? ????????? ????????? ??????????????? ????????????
            if (!room.getIsOpen() && !room.getPw().equals(request.getPw())) {
                throw new PasswordNotValidException();
            }

            Participant newParticipant = Participant.builder()
                    .room(room)
                    .user(user != null ? user : null)
                    .name(user != null ? user.getName() : name)
                    .enterDate(LocalDateTime.now())
                    .build();

            newParticipant = participantRepository.save(newParticipant);

            ParticipantOnRedis participantOnRedis = participantOnRedisRepository.findById(roomId).orElseThrow();
            participantOnRedis.addParticipant(newParticipant.getId(), token, connection.getConnectionId());
            participantOnRedisRepository.save(participantOnRedis);

            return RoomResponse.EnterRoom.builder()
                    .participantId(newParticipant.getId())
                    .token(token)
                    .connectionId(connection.getConnectionId())
                    .build();

        } catch (OpenViduJavaClientException e) {
            // If internal error generate an error message and return it to client
            throw new BusinessException(e.getMessage());
        } catch (OpenViduHttpException e) {
            handleUnexpectedlyInvalidSessionException(roomId, e);
            throw new BusinessException(e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 1. ????????? ?????? ?????? ????????? ????????????.
     */
    public RoomResponse.OnlyId exitRoom(Long roomId, Long participantId, User user) {

        try {
            RoomOnRedis roomOnRedis = roomOnRedisRepository.findById(roomId).orElseThrow();
            ParticipantOnRedis participantOnRedis = participantOnRedisRepository.findById(roomId).orElseThrow();

            Session session = getSession(roomOnRedis.getSessionId());
            Room room = roomRepository.findById(roomId).orElseThrow();
            if (user != null && Objects.equals(user.getId(), roomOnRedis.getCreatedBy())) {  // ????????? ????????????
                for (Long pid : participantOnRedis.getParticipantTokenMap().keySet()) {
                    addStampAndUpdateVisitedAtBeforeExit(room, pid);
                }
                participantOnRedis.getParticipantTokenMap().clear();
                participantOnRedis.getParticipantConnectionMap().clear();
            } else {
                addStampAndUpdateVisitedAtBeforeExit(room, participantId);
                participantOnRedis.removeParticipant(participantId);
            }
            if (participantOnRedis.getParticipantTokenMap().isEmpty()) {  // ?????? ????????? ???????????? ????????????
                // ?????? ??????
                session.close();
                log.info("Session destroyed");
                participantOnRedisRepository.deleteById(roomId);
                roomOnRedisRepository.deleteById(roomId);
                room = roomRepository.findById(roomId).orElseThrow();
                room.setDeletedAt(LocalDateTime.now());
            } else {  // ?????? ?????? ???????????? ????????????
                log.info("?????? ?????? ???????????? ????????????.");
                participantOnRedisRepository.save(participantOnRedis);
            }

        } catch (OpenViduJavaClientException e) {
            // If internal error generate an error message and return it to client
            throw new BusinessException(e.getMessage());
        } catch (OpenViduHttpException e) {
            handleUnexpectedlyInvalidSessionException(roomId, e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return RoomResponse.OnlyId.builder().roomId(roomId).build();
    }

    /**
     * default 5?????? ???????????? ??????????????? ?????? ?????? ?????? ?????? 5?????? ????????????.
     * ??? ??????????????? ???????????? ????????? roomId?????? ?????? 5?????? ????????????.
     * ??????: Redis?????? ?????? ???????????? ????????? ????????????.
     */
    public Page<RoomResponse.SearchRooms> search(RoomRequest.RoomSearchCondition condition, Pageable pageable) {

        Page<RoomResponse.SearchRooms> page = roomRepository.search(condition, pageable);
        Iterator<RoomResponse.SearchRooms> searchedRoomsIterator = page.getContent().iterator();
        boolean isInConsistent = false;

        Set<String> activeSessionIds = this.openVidu.getActiveSessions().stream().map(Session::getSessionId).collect(Collectors.toSet());
        Iterable<RoomOnRedis> allRooms = roomOnRedisRepository.findAll();
        Iterator<RoomOnRedis> allRoomsIterator = allRooms.iterator();
        while (allRoomsIterator.hasNext()) {
            RoomOnRedis roomOnRedis = allRoomsIterator.next();

            if (activeSessionIds.contains(roomOnRedis.getSessionId())) {
                // ????????? ???????????? ????????? 1. MySQL?????? deletedAt??? ???????????? ?????? ???????????? ?????????????????? Redis?????? ?????? ???????????? ????????? ??????
                Room room = roomRepository.findById(roomOnRedis.getId()).orElseThrow();
                if (room.getDeletedAt() != null) {
                    isInConsistent = true;
                    roomOnRedisRepository.deleteById(room.getId());
                    participantOnRedisRepository.deleteById(room.getId());
                }
            } else { // ????????? ???????????? ????????? 0. OpenVidu?????? active session??? ???????????? redis??? MySQL?????? active session??? ??????
                isInConsistent = true;
                Room room = roomRepository.findById(roomOnRedis.getId()).orElseThrow();
                room.setDeletedAt(LocalDateTime.now());
                roomOnRedisRepository.deleteById(room.getId());
                participantOnRedisRepository.deleteById(room.getId());
            }
        }

        // ????????? ???????????? ????????? 2. Redis?????? ?????? ??????????????? MySQL?????? ?????? ???????????? ????????? ??????
        while (searchedRoomsIterator.hasNext()) {
            RoomResponse.SearchRooms searchedRoom = searchedRoomsIterator.next();
            Optional<ParticipantOnRedis> participantOrNull = participantOnRedisRepository.findById(searchedRoom.getRoomId());
            // redis??? mysql??? ????????? ????????? ??????
            if (participantOrNull.isEmpty()) {
                // 1. ????????? ???????????? ??????
                isInConsistent = true;
                // 2. ???????????? ?????? -> Room ???????????? deletedAt??? ??????????????? ??????
                resolveInconsistency();
                break;
            }
            // ?????? ????????? ??????
            Integer curNum = participantOrNull.orElseThrow().getParticipantTokenMap().size();
            searchedRoom.setCurNum(curNum);
        }
        // ?????? ??????????????? ?????????????????? ?????? ????????????.
        if (isInConsistent) {
            page = roomRepository.search(condition, pageable);
            for (RoomResponse.SearchRooms searchedRoom : page.getContent()) {
                Integer curNum = participantOnRedisRepository.findById(searchedRoom.getRoomId())
                        .map(participantOnRedis -> participantOnRedis.getParticipantTokenMap().size())
                        .orElse(0);
                searchedRoom.setCurNum(curNum);
            }
        }
        return page;
    }

    @Transactional(readOnly = true)
    public RoomResponse.RoomInfo getRoomInfo(Long roomId) {
        RoomOnRedis roomOnRedis = roomOnRedisRepository.findById(roomId).orElseThrow();
        ParticipantOnRedis participantOnRedis = participantOnRedisRepository.findById(roomId).orElseThrow();
        Room room = roomRepository.findById(roomId).orElseThrow();
        return RoomResponse.RoomInfo.builder()
                .roomId(roomId)
                .sessionId(roomOnRedis.getSessionId())
                .categoryId(room.getCategory())
                .createdBy(room.getCreatedBy().getId())
                .title(room.getTitle())
                .rule(room.getRule())
                .imageId(room.getImage() != null ? room.getImage().getId() : null)
                .maxNum(room.getMaxNum())
                .curNum(participantOnRedis.getParticipantTokenMap().size())
                .isOpen(room.getIsOpen())
                .build();
    }

    @Transactional(readOnly = true)
    public RoomResponse.RoomDetail getRoomDetail(Long roomId) {
        return RoomResponse.RoomDetail.builder()
                .roomInfo(getRoomInfo(roomId))
                .participantList(getParticipants(roomId))
                .build();
    }

    @Transactional(readOnly = true)
    public List<RoomResponse.RoomDetail.ParticipantInfo> getParticipants(Long roomId) {
        ParticipantOnRedis participantOnRedis = participantOnRedisRepository.findById(roomId).orElseThrow();
        Map<Long, String> participantTokenMap = participantOnRedis.getParticipantTokenMap();
        Map<Long, String> participantConnectionMap = participantOnRedis.getParticipantConnectionMap();
        List<RoomResponse.RoomDetail.ParticipantInfo> participantInfos = participantRepository.findActiveParticipants(participantTokenMap.keySet());
        participantInfos.forEach(participantInfo -> {
            participantInfo.setToken(participantTokenMap.get(participantInfo.getParticipantId()));
            participantInfo.setConnectionId(participantConnectionMap.get(participantInfo.getParticipantId()));
        });
        return participantInfos;
    }

    /**
     * 1. ???????????? ????????? ???????????? ????????????.
     * 2. ????????? ?????? ???????????? ????????????.
     * 3. maxNum??? curNum?????? ?????? ????????????. ????????? ?????? ????????? ?????? ???????????? ??????.
     */
    public RoomResponse.OnlyId updateRoomInfo(Long roomId, RoomRequest.UpdateRoom request, User user) {

        if (isValidated(roomId, user)) {
            throw new BusinessException("??? ?????? ????????? ????????????.");
        }

        if (participantOnRedisRepository.findById(roomId).orElseThrow().getParticipantTokenMap().size() > request.getMaxNum()) {
            throw new BusinessException("?????? ?????? ???????????? ?????? ????????? ??? ????????????.");
        }

        Room foundRoom = roomRepository.findById(roomId).orElseThrow();
        foundRoom.updateInfo(request);
        return RoomResponse.OnlyId.builder().roomId(roomId).build();
    }

    public RoomResponse.OnlyId updateRoomImage(Long roomId, MultipartFile file, User user) {

        if (isValidated(roomId, user)) {
            throw new BusinessException("??? ?????? ????????? ????????????.");
        }

        Image newImage = null;
        if (!file.isEmpty()) {
            Long newImageId = imageService.save(file).getId();
            newImage = imageRepository.findById(newImageId).orElseThrow();
        }

        Room foundRoom = roomRepository.findById(roomId).orElseThrow();
        foundRoom.updateImage(newImage);
        return RoomResponse.OnlyId.builder().roomId(roomId).build();
    }

    private boolean isValidated(Long roomId, User user) {
        if (roomRepository.findById(roomId).orElseThrow().getUser().getId() != user.getId())
            return false;
        return true;
    }

    /**
     * 1. ???????????? ????????? ???????????? ????????????.
     * 2. ???????????? ?????? ????????? ??????????????? ????????????.
     * 3. participant??? exit_date??? ????????????.
     * 4. ???????????? ???????????? ?????? ??? ??????.
     */
    public RoomResponse.OnlyId dropoutParticipant(Long roomId, Long participantId, User user) {

        if (roomOnRedisRepository.findById(roomId).orElseThrow().getCreatedBy() != user.getId()) {
            throw new BusinessException("???????????? ???????????? ????????? ????????? ??? ????????????.");
        }

        if (participantOnRedisRepository.findById(roomId).orElseThrow().getParticipantTokenMap().get(participantId) == null) {
            throw new BusinessException("?????? ????????? ?????????????????? ????????? ??? ????????????.");
        }

        try {
            Session session = getSession(roomOnRedisRepository.findById(roomId).orElseThrow().getSessionId());
            ParticipantOnRedis participantOnRedis = participantOnRedisRepository.findById(roomId).orElseThrow();
            String connectionId = participantOnRedis.getParticipantConnectionMap().get(participantId);
            // OpenVidu API: force disconnect participant
            session.forceDisconnect(connectionId);
            participantOnRedis.removeParticipant(participantId);
            participantOnRedis = participantOnRedisRepository.save(participantOnRedis);
            Participant participant = participantRepository.findById(participantId).orElseThrow();
            participant.addExitDate();
            Dropout dropout = Dropout.builder()
                    .room(roomRepository.findById(roomId).orElseThrow())
                    .user(participant.getUser())
                    .name(participant.getName())
                    .build();
            dropoutRepository.save(dropout);
            if (participantOnRedis.getParticipantTokenMap().isEmpty()) {  // ?????? ????????? ???????????? ????????????
                // ?????? ??????
                session.close();
                participantOnRedisRepository.deleteById(roomId);
                roomOnRedisRepository.deleteById(roomId);
                Room room = roomRepository.findById(roomId).orElseThrow();
                room.setDeletedAt(LocalDateTime.now());
                log.info("Session destroyed");
            }
        } catch (OpenViduJavaClientException | OpenViduHttpException e) {
            throw new RuntimeException(e);
        }
        return RoomResponse.OnlyId.builder().roomId(roomId).build();
    }

    private void createStamp(DiaryRequest.StampCreate request, User user) {
        Stamp stamp = Stamp.create(user, request.getCategory(), request.getEnterTime(), request.getExitTime());
        stampRepository.save(stamp);
    }

    private Session getSession(String sessionId) {
        List<Session> activeSessions = this.openVidu.getActiveSessions();
        return activeSessions.stream().filter(s -> s.getSessionId().equals(sessionId)).findFirst().orElseThrow();
    }

    private void handleUnexpectedlyInvalidSessionException(Long roomId, OpenViduHttpException e) {
        if (404 == e.getStatus()) {
            // Invalid sessionId (user left unexpectedly). Session object is not valid
            // anymore. Clean collections and continue as new session
            roomOnRedisRepository.deleteById(roomId);
            participantOnRedisRepository.deleteById(roomId);
            Room room = roomRepository.findById(roomId).orElseThrow();
            room.setDeletedAt(LocalDateTime.now());
        }
    }

    private void addStampAndUpdateVisitedAtBeforeExit(Room room, Long participantId) {
        Participant participant = participantRepository.findById(participantId).orElseThrow();
        participant.addExitDate();

        if (participant.getUser() != null) {
            // ????????? ??????
            createStamp(DiaryRequest.StampCreate.builder()
                    .category(room.getCategory())
                    .enterTime(participant.getEnterDate())
                    .exitTime(participant.getExitDate())
                    .build(), participant.getUser());
            // ?????? ???????????? ??????
            participant.getUser().updateVisitedAt();
        }
    }

    /**
     * Redis??? MySQL?????? ???????????? ?????? Room??? ??????????????? ????????????.
     * Redis??? MySQL ???????????? ???????????? ?????? Room?????? ???????????? ?????? ????????? ????????????.
     * -> ????????? ??????????????? Redis??? ??????????????? MySQL?????? ?????? ???????????? Room?????? ???????????? ????????? ???????????? ?????? ????????????.
     */
    private void resolveInconsistency() {
        List<Room> searchedUndeletedRooms = roomRepository.searchUndeletedRooms();
        for (Room searchedUndeletedRoom : searchedUndeletedRooms) {
            if (roomOnRedisRepository.findById(searchedUndeletedRoom.getId()).isEmpty()) {
                Room room = roomRepository.findById(searchedUndeletedRoom.getId()).orElseThrow();
                room.setDeletedAt(LocalDateTime.now());
            }
        }
    }
}