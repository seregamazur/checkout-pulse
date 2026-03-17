package com.seregamazur.pulse;

import org.springframework.boot.SpringApplication;

public class TestCheckoutPulseApplication {

	public static void main(String[] args) {
		SpringApplication.from(CheckoutPulseApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
