package com.wayfare.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

@Configuration
public class JacksonConfig {

    public static final String VIETNAM_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'+07:00'";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            // Múi giờ mặc định cho Jackson là Giờ Việt Nam (UTC+7)
            builder.timeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
            
            // Format cho LocalDateTime: Tự động đính kèm offset +07:00 để client luôn nhận chuẩn múi giờ Việt Nam
            DateTimeFormatter serializerFormatter = DateTimeFormatter.ofPattern(VIETNAM_DATETIME_FORMAT);
            builder.serializers(new LocalDateTimeSerializer(serializerFormatter));
            
            // Deserializer chấp nhận mọi định dạng ISO chuẩn (có hoặc không có offset)
            builder.deserializers(new LocalDateTimeDeserializer(DateTimeFormatter.ISO_DATE_TIME));
        };
    }
}
