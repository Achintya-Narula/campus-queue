package com.achintya.campusqueue.workshop;

import com.achintya.campusqueue.common.api.PageResponse;
import com.achintya.campusqueue.common.error.FieldErrorDetail;
import com.achintya.campusqueue.common.error.RequestValidationException;
import com.achintya.campusqueue.workshop.dto.WorkshopResponse;
import java.util.UUID;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workshops")
public class WorkshopController {

    private final WorkshopService workshopService;

    public WorkshopController(WorkshopService workshopService) {
        this.workshopService = workshopService;
    }

    @GetMapping
    PageResponse<WorkshopResponse> listPublished(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        return PageResponse.from(workshopService.listPublished(q, pageable));
    }

    @GetMapping("/{workshopId}")
    WorkshopResponse getPublished(@PathVariable UUID workshopId) {
        return workshopService.getPublished(workshopId);
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new RequestValidationException(List.of(
                    new FieldErrorDetail("page", "must be greater than or equal to 0")));
        }
        if (size < 1 || size > 50) {
            throw new RequestValidationException(List.of(
                    new FieldErrorDetail("size", "must be between 1 and 50")));
        }
    }
}
