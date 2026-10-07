package com.achintya.campusqueue.workshop;

import com.achintya.campusqueue.workshop.dto.WorkshopResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workshops")
public class WorkshopController {

    private final WorkshopService workshopService;

    public WorkshopController(WorkshopService workshopService) {
        this.workshopService = workshopService;
    }

    @GetMapping
    Page<WorkshopResponse> listPublished(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return workshopService.listPublished(pageable);
    }

    @GetMapping("/{workshopId}")
    WorkshopResponse getPublished(@PathVariable UUID workshopId) {
        return workshopService.getPublished(workshopId);
    }
}
