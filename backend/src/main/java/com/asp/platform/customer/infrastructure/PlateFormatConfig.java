package com.asp.platform.customer.infrastructure;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.asp.platform.customer.domain.PlateFormat;
import com.asp.platform.customer.domain.PlateFormats;
import com.asp.platform.customer.domain.PortuguesePlate;

/// Plate formats per registration country. Support a new country by adding its [PlateFormat] bean here.
@Configuration(proxyBeanMethods = false)
class PlateFormatConfig {

    @Bean
    PortuguesePlate portuguesePlate() {
        return new PortuguesePlate();
    }

    @Bean
    PlateFormats plateFormats(List<PlateFormat> formats) {
        return new PlateFormats(formats);
    }
}
