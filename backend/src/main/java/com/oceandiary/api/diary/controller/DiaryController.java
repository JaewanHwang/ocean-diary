package com.oceandiary.api.diary.controller;


import com.oceandiary.api.diary.dto.DiaryRequest;
import com.oceandiary.api.diary.dto.DiaryResponse;
import com.oceandiary.api.diary.service.DiaryService;
import com.oceandiary.api.user.security.userdetails.CurrentUser;
import com.oceandiary.api.user.security.userdetails.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/diary")
public class DiaryController {
    private final DiaryService diaryService;

    @PostMapping("/stamp")
    public ResponseEntity<DiaryResponse.StampOnlyId> create(@RequestBody DiaryRequest.StampCreate request, @CurrentUser CustomUserDetails customUserDetails) {
        DiaryResponse.StampOnlyId response = diaryService.create(request, customUserDetails.getUser());
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<DiaryResponse.GetDiaryContents> getDiary(@PathVariable("userId") Long userId) {
        DiaryResponse.GetDiaryContents response = diaryService.getDiaryInfo(userId);
        return ResponseEntity.ok().body(response);
    }

    @PatchMapping("/user/info/{userId}")
    public ResponseEntity<DiaryResponse.UserInfo> updateUserInfo(@PathVariable("userId") Long userId,@RequestBody DiaryRequest.UserInfo request){
        DiaryResponse.UserInfo userInfoResponse = diaryService.updateUserInfo(userId, request);
        return ResponseEntity.ok().body(userInfoResponse);
    }

    // TODO : uri path 수정
    @PatchMapping("/user/account/{userId}")
    public void deleteUserInfo(@PathVariable("userId") Long userId){
        diaryService.deleteUserInfo(userId);
    }
}
