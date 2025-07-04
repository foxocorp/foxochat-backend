package app.foxochat.constant;

import lombok.Getter;

public class MediaConstant {

	@Getter
	public enum Flags {
		SPOILER(1);

		private final long bit;

		Flags(long bit) {
			this.bit = bit;
		}
	}
}
