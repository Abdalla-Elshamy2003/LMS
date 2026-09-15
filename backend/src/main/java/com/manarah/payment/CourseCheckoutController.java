package com.manarah.payment;

import com.manarah.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/checkout")
public class CourseCheckoutController {
    private final CourseCheckoutService service;
    public CourseCheckoutController(CourseCheckoutService service) { this.service = service; }
    @PostMapping("/orders") public CourseCheckoutService.OrderView create(@AuthenticationPrincipal UserPrincipal actor, @RequestBody CourseCheckoutService.CreateOrder req) { return service.create(actor, req); }
    @GetMapping("/orders/{reference}") public CourseCheckoutService.OrderView get(@AuthenticationPrincipal UserPrincipal actor, @PathVariable String reference) { return service.get(actor, reference); }
    @PostMapping("/orders/{reference}/pay") public CourseCheckoutService.OrderView pay(@AuthenticationPrincipal UserPrincipal actor, @PathVariable String reference, @RequestBody CourseCheckoutService.PayRequest req) { return service.pay(actor, reference, req); }
}
