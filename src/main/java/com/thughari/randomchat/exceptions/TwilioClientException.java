package com.thughari.randomchat.exceptions;

public class TwilioClientException extends Exception {

	private static final long serialVersionUID = 1L;

	public TwilioClientException(String message) {
		super(message);
	}

	public TwilioClientException(String message, Throwable cause) {
		super(message, cause);
	}
}