package com.suixingpay.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.suixingpay.service.TicketService;
import com.suixingpay.service.impl.TicketServiceImpl;


@RestController
@RequestMapping("/Tickets")
public class TicketController {
    @Autowired
    private TicketServiceImpl ticketService;

    @GetMapping("/buy")
    public Object buyTickets(@RequestParam("username") String username){
        return ticketService.sellTickets(username);
    }

    @GetMapping("/show")
    public Object showTickets(){
        return ticketService.showCurrentProgress();
    }

}
