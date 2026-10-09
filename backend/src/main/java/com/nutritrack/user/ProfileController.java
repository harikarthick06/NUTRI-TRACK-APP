package com.nutritrack.user;

import com.nutritrack.user.ProfileDtos.ProfileResponse;
import com.nutritrack.user.ProfileDtos.TargetSuggestion;
import com.nutritrack.user.ProfileDtos.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@Tag(name = "Profile")
public class ProfileController {
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    @Operation(summary = "Get the current user's profile and goals")
    public ProfileResponse get() {
        return profileService.get();
    }

    @PutMapping
    @Operation(summary = "Replace the current user's profile and goals")
    public ProfileResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return profileService.update(request);
    }

    @GetMapping("/target-suggestions")
    @Operation(summary = "Suggest editable calorie, macro, fibre and water targets from the profile")
    public TargetSuggestion suggestions() {
        return profileService.suggestTargets();
    }
}
