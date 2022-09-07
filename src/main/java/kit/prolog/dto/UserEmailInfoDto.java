package kit.prolog.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@AllArgsConstructor
public class UserEmailInfoDto {
    private String name;
    private String account;
    private String password;
    private String email;
    private boolean alarm;
    private String image;
    private String nickname;
    private String introduce;
}