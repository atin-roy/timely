package dev.atinroy.backend.controller;

import dev.atinroy.backend.dto.tag.TagRequest;
import dev.atinroy.backend.dto.tag.TagResponse;
import dev.atinroy.backend.security.UserDetailsImpl;
import dev.atinroy.backend.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public ResponseEntity<List<TagResponse>> getAllTags(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        List<TagResponse> tags = tagService.getAllTagsByUser(userDetails.getId());
        return ResponseEntity.ok(tags);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TagResponse> getTagById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        TagResponse tag = tagService.getTagById(id, userDetails.getId());
        return ResponseEntity.ok(tag);
    }

    @PostMapping
    public ResponseEntity<TagResponse> createTag(
            @Valid @RequestBody TagRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        TagResponse tag = tagService.createTag(request, userDetails.getId());
        return new ResponseEntity<>(tag, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TagResponse> updateTag(
            @PathVariable Long id,
            @Valid @RequestBody TagRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        TagResponse tag = tagService.updateTag(id, request, userDetails.getId());
        return ResponseEntity.ok(tag);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        tagService.deleteTag(id, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
