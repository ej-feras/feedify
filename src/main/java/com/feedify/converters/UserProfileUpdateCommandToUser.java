package com.feedify.converters;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import com.feedify.commands.UserProfileUpdateCommand;
import com.feedify.database.entity.User;

@Component
public class UserProfileUpdateCommandToUser implements Converter<UserProfileUpdateCommand, User> {

    @Nullable
    @Override
    public User convert(UserProfileUpdateCommand source) {
        if (source == null) {
            return null;
        }

        final User user = new User();
        user.setUsername(source.getUserName());
        user.setEmail(source.getEmail());
        user.setFirstName(source.getFirstName());
        user.setLastName(source.getLastName());
        user.setCountry(source.getCountry());
        user.setJob(source.getJob());
        user.setAge(source.getAge());

        return user;
    }
}
