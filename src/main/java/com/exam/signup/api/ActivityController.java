package com.exam.signup.api;

import com.exam.signup.api.dto.ActivityResponse;
import com.exam.signup.api.dto.ApiData;
import com.exam.signup.api.dto.PageResponse;
import com.exam.signup.application.ActivityQueryService;
import com.exam.signup.application.InputRules;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ActivityController {

    private final ActivityQueryService activityQueryService;

    public ActivityController(ActivityQueryService activityQueryService) {
        this.activityQueryService = activityQueryService;
    }

    @GetMapping("/api/activities")
    public ApiData<PageResponse<ActivityResponse>> list(@RequestParam(value = "page", required = false) String page,
                                                         @RequestParam(value = "size", required = false) String size) {
        return new ApiData<>(activityQueryService.list(InputRules.parsePage(page, size)));
    }

    @GetMapping("/api/activities/{id}")
    public ApiData<ActivityResponse> detail(@PathVariable("id") String id) {
        return new ApiData<>(activityQueryService.detail(InputRules.parseActivityPathId(id)));
    }
}
