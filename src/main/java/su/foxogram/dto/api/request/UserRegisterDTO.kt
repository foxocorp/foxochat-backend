package su.foxogram.dto.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import su.foxogram.constant.ValidationConstant;

@Setter
@Getter
@Schema(name = "UserRegister")
public class UserRegisterDTO {

	@NotNull(message = "Username" + ValidationConstant.Messages.MUST_NOT_BE_NULL)
	@Size(min = ValidationConstant.Lengths.MIN, max = ValidationConstant.Lengths.USERNAME, message = ValidationConstant.Messages.USERNAME_WRONG_LENGTH)
	@Pattern(regexp = ValidationConstant.Regex.NAME_REGEX, message = ValidationConstant.Messages.USERNAME_INCORRECT)
	private String username;

	@NotNull(message = "Email" + ValidationConstant.Messages.MUST_NOT_BE_NULL)
	@Size(min = ValidationConstant.Lengths.MIN, max = ValidationConstant.Lengths.EMAIL, message = ValidationConstant.Messages.EMAIL_WRONG_LENGTH)
	@Pattern(regexp = ValidationConstant.Regex.EMAIL_REGEX, message = ValidationConstant.Messages.EMAIL_INCORRECT)
	private String email;

	@NotNull(message = "Password" + ValidationConstant.Messages.MUST_NOT_BE_NULL)
	@Size(min = ValidationConstant.Lengths.MIN, max = ValidationConstant.Lengths.PASSWORD, message = ValidationConstant.Messages.PASSWORD_WRONG_LENGTH)
	private String password;
}
