package realworld_backend.dto;

import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.security.Timestamp;
import java.util.Date;

@Table(name="apiResponse")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data ;
    private long timestamp;

    public static <T> ApiResponse success(T data){
        return new ApiResponse<>(200,"success",data,new Date().getTime());
    }
    public static <T> ApiResponse error(int code,String message){
        return new ApiResponse<>(code,message,null,new Date().getTime());
    }
}
