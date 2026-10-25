package com.atinroy.timely.session;

import com.atinroy.timely.api.model.SessionKindDto;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

// Spring binds enum query parameters by constant name, but the API sends the lowercase value.
@Component
class SessionKindConverter implements Converter<String, SessionKindDto> {
    @Override
    public SessionKindDto convert(String source) {
        return SessionKindDto.fromValue(source);
    }
}
