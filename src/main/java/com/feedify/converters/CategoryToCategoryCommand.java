package com.feedify.converters;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import com.feedify.commands.CategoryCommand;
import com.feedify.database.entity.Category;
import com.feedify.database.entity.User;
import com.feedify.rest.service.FeedsService;

import lombok.Data;

@Component
@Data
public class CategoryToCategoryCommand implements Converter<Category, CategoryCommand> {

    private User user;
    private FeedsService feedService;

    public CategoryToCategoryCommand(FeedsService feedService) {
        this.feedService = feedService;
    }

    @Nullable
    @Override
    public CategoryCommand convert(Category source) {
        if (source == null) {
            return null;
        }

        final CategoryCommand categoryCOommand = new CategoryCommand();
        String categoryName = source.getName();
        categoryCOommand.setName(categoryName);
        categoryCOommand.setNumberOfUnreadFeeds(feedService.findNumberOfUnreadFeedsOfCategory(user, categoryName));

        return categoryCOommand;
    }

}
