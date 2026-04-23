package realworld_backend.common.exception;

import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;


@Table(name="error")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Error {
    private HashMap<String,String> errorForm;
}

