package com.manarah.assistant;

import com.manarah.assistant.AssistantService.*;
import com.manarah.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** The teacher's-assistant workspace. Open to the teacher (who assigns work and reads the follow-ups) and their assistants. */
@RestController
@RequestMapping("/api/assistant")
@PreAuthorize("hasAnyRole('TEACHER','ASSISTANT')")
@Tag(name = "Teacher assistant")
public class AssistantController {

    private final AssistantService service;

    public AssistantController(AssistantService service) {
        this.service = service;
    }

    @GetMapping("/desk")
    public Map<String, Object> desk(@AuthenticationPrincipal UserPrincipal actor) { return service.desk(actor); }

    @GetMapping("/tasks")
    public List<TaskView> tasks(@AuthenticationPrincipal UserPrincipal actor) { return service.tasks(actor); }

    @PostMapping("/tasks")
    public TaskView createTask(@AuthenticationPrincipal UserPrincipal actor, @RequestBody TaskRequest req) {
        return service.createTask(actor, req);
    }

    @PutMapping("/tasks/{id}/status")
    public TaskView taskStatus(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestBody StatusRequest req) {
        return service.setTaskStatus(actor, id, req);
    }

    @DeleteMapping("/tasks/{id}")
    public void deleteTask(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) { service.deleteTask(actor, id); }

    @GetMapping("/notes")
    public List<NoteView> notes(@AuthenticationPrincipal UserPrincipal actor, @RequestParam(required = false) Long studentId,
                                @RequestParam(defaultValue = "false") boolean openOnly) {
        return service.notes(actor, studentId, openOnly);
    }

    @PostMapping("/notes")
    public NoteView addNote(@AuthenticationPrincipal UserPrincipal actor, @RequestBody NoteRequest req) {
        return service.addNote(actor, req);
    }

    @PutMapping("/notes/{id}/status")
    public NoteView noteStatus(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestBody StatusRequest req) {
        return service.setNoteStatus(actor, id, req);
    }

    @DeleteMapping("/notes/{id}")
    public void deleteNote(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) { service.deleteNote(actor, id); }
}
