package com.thughari.randomchat.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.thughari.randomchat.exceptions.TwilioClientException;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Base64;

@Component
public class TwilioHttpClient {

	private static final Logger logger = LoggerFactory.getLogger(TwilioHttpClient.class);

	@Value("${twilio.account.sid}")
	private String twilioAccountSid;

	@Value("${twilio.auth.token}")
	private String twilioAuthToken;

	private final RestTemplate restTemplate;

	public TwilioHttpClient() {
		this.restTemplate = new RestTemplate();
	}

	/**
	 * Makes an HTTP POST request to Twilio's Tokens API to fetch ICE servers.
	 *
	 * @return The raw JSON response body from Twilio.
	 * @throws TwilioClientException if the API call fails or returns a non-successful status.
	 */
	public String fetchTwilioTokensApiResponse() throws TwilioClientException {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		String auth = twilioAccountSid + ":" + twilioAuthToken;
		String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
		headers.add("Authorization", "Basic " + encodedAuth);

		HttpEntity<String> requestEntity = new HttpEntity<>(headers);

		String twilioApiUrl = String.format("https://api.twilio.com/2010-04-01/Accounts/%s/Tokens.json", twilioAccountSid);

		try {
			logger.debug("Making Twilio Tokens API call to: {}", twilioApiUrl);
			ResponseEntity<String> response = restTemplate.exchange(
					twilioApiUrl,
					HttpMethod.POST,
					requestEntity,
					String.class
					);

			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
				logger.debug("Successfully received response from Twilio API.");
				return response.getBody();
			} else {
				logger.error("Twilio API call failed with status: {} and body: {}", response.getStatusCode(), response.getBody());
				throw new TwilioClientException("Twilio API returned non-2xx status: " + response.getStatusCode());
			}
		} catch (HttpClientErrorException | HttpServerErrorException e) {
			logger.error("Twilio API client/server error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
			throw new TwilioClientException("Twilio API returned an HTTP error: " + e.getStatusCode(), e);
		} catch (ResourceAccessException e) {
			logger.error("Network or connection error connecting to Twilio API: {}", e.getMessage(), e);
			throw new TwilioClientException("Network error connecting to Twilio API", e);
		} catch (Exception e) {
			logger.error("An unexpected error occurred during Twilio API call: {}", e.getMessage(), e);
			throw new TwilioClientException("Unexpected error during Twilio API call", e);
		}
	}
}