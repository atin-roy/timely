package dev.atinroy.backend.service;

import dev.atinroy.backend.dto.tag.TagRequest;
import dev.atinroy.backend.dto.tag.TagResponse;
import dev.atinroy.backend.entity.Tag;
import dev.atinroy.backend.entity.User;
import dev.atinroy.backend.exception.DuplicateResourceException;
import dev.atinroy.backend.exception.ResourceNotFoundException;
import dev.atinroy.backend.exception.UnauthorizedException;
import dev.atinroy.backend.exception.ValidationException;
import dev.atinroy.backend.mapper.TagMapper;
import dev.atinroy.backend.repository.TagRepository;
import dev.atinroy.backend.repository.TimeBlockRepository;
import dev.atinroy.backend.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;
    private final TodoRepository todoRepository;
    private final TimeBlockRepository timeBlockRepository;
    private final UserService userService;
    private final TagMapper tagMapper;

    public List<TagResponse> getAllTagsByUser(Long userId) {
        return tagRepository.findByUserId(userId).stream()
                .map(tagMapper::toResponse)
                .collect(Collectors.toList());
    }

    public TagResponse getTagById(Long tagId, Long userId) {
        Tag tag = getTagAndValidateOwnership(tagId, userId);
        return tagMapper.toResponse(tag);
    }

    @Transactional
    public TagResponse createTag(TagRequest request, Long userId) {
        User user = userService.getUserById(userId);

        // Check if tag with same label already exists for this user
        if (tagRepository.existsByUserIdAndLabel(userId, request.getLabel())) {
            throw new DuplicateResourceException("Tag", "label", request.getLabel());
        }

        Tag tag = tagMapper.toEntity(request);
        tag.setUser(user);

        Tag savedTag = tagRepository.save(tag);
        return tagMapper.toResponse(savedTag);
    }

    @Transactional
    public TagResponse updateTag(Long tagId, TagRequest request, Long userId) {
        Tag tag = getTagAndValidateOwnership(tagId, userId);

        // Check if new label conflicts with existing tags (excluding current tag)
        if (!tag.getLabel().equals(request.getLabel()) &&
                tagRepository.existsByUserIdAndLabel(userId, request.getLabel())) {
            throw new DuplicateResourceException("Tag", "label", request.getLabel());
        }

        tagMapper.updateEntity(request, tag);
        Tag updatedTag = tagRepository.save(tag);
        return tagMapper.toResponse(updatedTag);
    }

    @Transactional
    public void deleteTag(Long tagId, Long userId) {
        Tag tag = getTagAndValidateOwnership(tagId, userId);

        // Check if tag is in use by todos
        long todosUsingTag = todoRepository.findByUserIdAndTagId(userId, tagId).size();
        if (todosUsingTag > 0) {
            throw new ValidationException(
                    String.format("Cannot delete tag. It is being used by %d todo(s)", todosUsingTag));
        }

        // Check if tag is in use by time blocks
        long timeBlocksUsingTag = timeBlockRepository.findByUserIdAndTagId(userId, tagId).size();
        if (timeBlocksUsingTag > 0) {
            throw new ValidationException(
                    String.format("Cannot delete tag. It is being used by %d time block(s)", timeBlocksUsingTag));
        }

        tagRepository.delete(tag);
    }

    public boolean isTagInUse(Long tagId, Long userId) {
        getTagAndValidateOwnership(tagId, userId);

        long todosUsingTag = todoRepository.findByUserIdAndTagId(userId, tagId).size();
        long timeBlocksUsingTag = timeBlockRepository.findByUserIdAndTagId(userId, tagId).size();

        return todosUsingTag > 0 || timeBlocksUsingTag > 0;
    }

    // Helper method

    private Tag getTagAndValidateOwnership(Long tagId, Long userId) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag", "id", tagId));

        if (!tag.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("You don't have permission to access this tag");
        }

        return tag;
    }
}
