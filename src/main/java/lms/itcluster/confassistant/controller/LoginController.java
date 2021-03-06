package lms.itcluster.confassistant.controller;

import lms.itcluster.confassistant.dto.SignUpDTO;
import lms.itcluster.confassistant.dto.UserDTO;
import lms.itcluster.confassistant.model.CurrentUser;
import lms.itcluster.confassistant.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import javax.validation.Valid;

@Slf4j
@Controller
public class LoginController {

    @Autowired
    private UserService userService;

    @GetMapping("/login")
    public String getLoginHands (@AuthenticationPrincipal CurrentUser currentUser) {
        if (currentUser != null) {
            return "redirect:/";
        }
        return "login/login";
    }

    @GetMapping("/sign-up-guest")
    public String getLoginGuest (@AuthenticationPrincipal CurrentUser currentUser, Model model) {
        if (currentUser != null) {
            return "redirect:/";
        }
        model.addAttribute("signForm", new SignUpDTO());
        return "login/sign-up-guest";
    }

    @PostMapping("/sign-up-guest")
    public String saveGuest (@Valid @ModelAttribute("signForm") SignUpDTO signForm, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            return "login/sign-up-guest";
        }
        try {
            userService.createNewUserAsGuest(signForm);
        } catch (DataIntegrityViolationException e) {
            bindingResult.rejectValue("email", "user.already.exist", "User with this email address already exist");
            return "login/sign-up-guest";
        }
        model.addAttribute("message", "On your email address was sent link. Please check your email to active your profile.");
        return "message";
    }

    @GetMapping("/active/{code}")
    public String activeProfile(@PathVariable("code") String code, @AuthenticationPrincipal CurrentUser currentUser, Model model) {
        UserDTO userDTO = userService.completeRegistration(code, currentUser);
        if (userDTO != null) {
            model.addAttribute("message", "Your email was successfully confirmed");
            return "message";
        } else {
            model.addAttribute("message", "Not found");
            return "message";
        }
    }

}
