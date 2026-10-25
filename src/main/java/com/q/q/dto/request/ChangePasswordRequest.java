package com.q.q.dto.request;

import com.q.q.validation.MaxUtf8Bytes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
		@NotBlank String currentPassword,
		@NotBlank @Size(min = 8, max = 72) @MaxUtf8Bytes(72) String newPassword) {      // RN-11

	@Override
	public String toString() {
		return "ChangePasswordRequest[currentPassword=****, newPassword=****]";
	}

}
