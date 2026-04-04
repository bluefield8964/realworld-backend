package realworld_backend.dto;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import realworld_backend.model.User;


@Table(name="userRequest")
@Data
@NoArgsConstructor
@AllArgsConstructor

public class UserRequest {
    private User user;
}
