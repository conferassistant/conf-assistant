package lms.itcluster.confassistant.dto;

import lms.itcluster.confassistant.annotation.UniqueEmail;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class EditContactsDTO extends AbstractDTO{
    private Long userId;

    @UniqueEmail()
    private String email;

    @Override
    public Long getId() {
        return userId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
