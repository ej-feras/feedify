package com.feedify.rest.service.search;

import static com.feedify.constants.Constants.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.feedify.database.MaterializedViewManager;
import com.feedify.database.entity.Channel;
import com.feedify.database.entity.ChannelUser;
import com.feedify.database.entity.FeedItemUser;
import com.feedify.database.entity.User;
import com.feedify.database.entity.compositeIds.FeedItemUserId;
import com.feedify.database.repository.ChannelUserRepository;
import com.feedify.database.repository.RecentlyReadFeedItemUserRepository;
import com.feedify.rest.service.sortingandfiltering.CustomPagination;

@Service
public class RecentlyReadFeedsSearchingService {
	@Autowired
	private ChannelUserRepository channelUserRepository;
	@Autowired
	private RecentlyReadFeedItemUserRepository recentlyReadFeedItemUserRepository;
	@Autowired
	private CustomPagination customPagination;
	@Autowired
	private MaterializedViewManager materializedViewManager;

	/**
	 * @param user
	 * @param period
	 * @param order
	 * @param pageNumber
	 * @return List<FeedItem>
	 */
	public List<FeedItemUser> findFeedItemsUser(String q, User user, String period, String order, int pageNumber) {

		List<FeedItemUser> feedItemsUser = Collections.emptyList();

		switch (period) {
		case PERIOD_ALL:
			feedItemsUser = findFilteredAndOrderedFeedItemsUser(q, user, null, order, pageNumber);
			break;

		case PERIOD_TODAY:
			LocalDate today = LocalDate.now();
			feedItemsUser = findFilteredAndOrderedFeedItemsUser(q, user, today, order, pageNumber);
			break;

		case PERIOD_LAST_SEVEN_DAYS:
			LocalDate dateBeforeSevenDays = LocalDate.now().minusDays(7L);
			feedItemsUser = findFilteredAndOrderedFeedItemsUser(q, user, dateBeforeSevenDays, order, pageNumber);
			break;

		case PERIOD_LAST_THIRTY_DAYS:
			LocalDate dateBeforeThirtyDays = LocalDate.now().minusDays(30L);
			feedItemsUser = findFilteredAndOrderedFeedItemsUser(q, user, dateBeforeThirtyDays, order, pageNumber);
			break;

		default: {
			feedItemsUser = findFilteredAndOrderedFeedItemsUser(q, user, null, order, pageNumber);
			break;
		}
		}
		return feedItemsUser;
	}

	/**
	 * @param user
	 * @param startDate
	 * @param order
	 * @param pageNumber
	 * @return List<FeedItemUser>
	 */
	private List<FeedItemUser> findFilteredAndOrderedFeedItemsUser(String q, User user, LocalDate startDate,
			String order, Integer pageNumber) {

		List<FeedItemUser> feedItemsUser = searchAndFilterByPublishLocalDate(user.getId(), q, pageNumber, startDate);

		switch (order) {
		case ORDER_BY_LATEST: {
			Comparator<FeedItemUser> compareByLastReadingDateDesc = Comparator.comparing(f -> f.getLastReadingDate(),
					Comparator.nullsLast(Comparator.reverseOrder()));
			Collections.sort(feedItemsUser, compareByLastReadingDateDesc);
			break;
		} // end case

		case ORDER_BY_OLDEST: {
			Comparator<FeedItemUser> compareByLastReadingDateAsc = Comparator.comparing(f -> f.getLastReadingDate(),
					Comparator.nullsLast(Comparator.naturalOrder()));
			Collections.sort(feedItemsUser, compareByLastReadingDateAsc);
			break;
		} // end case

		case ORDER_BY_CHANNEL: {
			Comparator<FeedItemUser> compareByChannelTitle = Comparator
					.comparing(f -> f.getFeedItem().getChannel().getTitle());
			Comparator<FeedItemUser> compareByLastReadingDateDesc = Comparator.comparing(f -> f.getLastReadingDate(),
					Comparator.nullsLast(Comparator.reverseOrder()));
			Comparator<FeedItemUser> compareByChannelTitleAndPublishDate = compareByChannelTitle
					.thenComparing(compareByLastReadingDateDesc);
			Collections.sort(feedItemsUser, compareByChannelTitleAndPublishDate);
			;
			break;
		} // end case

		case ORDER_BY_ALL_CATEGORIES: {
			feedItemsUser = findFeedItemUsersByRecentlyReadOrderedByCategoryName(user, pageNumber, feedItemsUser);
			break;
		} // end case

		default:
			return feedItemsUser;
		}// end switch

		return feedItemsUser;
	}

	/**
	 * @param user
	 * @param liked
	 * @param pageNumber
	 * @param feedItemsUser
	 * @return List<FeedItemUser>
	 */
	private List<FeedItemUser> findFeedItemUsersByRecentlyReadOrderedByCategoryName(User user, Integer pageNumber,
			List<FeedItemUser> feedItemsUser) {

		Map<String, List<FeedItemUser>> feedItemUsersOfCategoryMap = new HashMap<String, List<FeedItemUser>>();

		feedItemsUser.forEach(fu -> {
			Channel channel = fu.getFeedItem().getChannel();
			Optional<ChannelUser> optionalChannelUser = channelUserRepository.findByUserAndChannel(user, channel);
			if (optionalChannelUser.isPresent()) {
				String categoryNameOfFeedItemUser = optionalChannelUser.get().getCategory().getName();
				if (fu.getLastReadingDate() != null)
					feedItemUsersOfCategoryMap.computeIfAbsent(categoryNameOfFeedItemUser, k -> new ArrayList<>())
							.add(fu);
			}
		});

		List<FeedItemUser> result = new ArrayList<>();
		feedItemUsersOfCategoryMap.values().forEach(list -> result.addAll(list));

		return result;
	}

	/**
	 * @param userId
	 * @param q
	 * @param pageNumber
	 * @param startDate
	 * @return List<FeedItemUser>
	 */
	private List<FeedItemUser> searchAndFilterByPublishLocalDate(Long userId, String q, Integer pageNumber,
			LocalDate startDate) {
		List<FeedItemUser> feedItemsUser = search(userId, q, pageNumber);
		if (startDate == null)
			return feedItemsUser;
		else {
			feedItemsUser = feedItemsUser.stream().filter(f -> {
				if (f.getFeedItem().getPublishLocalDate() != null)
					return f.getFeedItem().getPublishLocalDate().isAfter(startDate)
							|| f.getFeedItem().getPublishLocalDate().isEqual(startDate);
				return false;
			}).collect(Collectors.toList());
		}
		return feedItemsUser;
	}

	/**
	 * @param userId
	 * @param query
	 * @param pageNumber
	 * @return List<FeedItemUser>
	 */
	private List<FeedItemUser> search(Long userId, String query, Integer pageNumber) {

		List<Long> feedItemsIds = materializedViewManager.fullTextSearchFeedItem(query);
		List<Long> feedItemsIdsPage = customPagination.getNextPageOfFeedItemsIds(pageNumber, PAGE_SIZE, feedItemsIds);

		List<FeedItemUserId> feedItemUsersIds = feedItemsIdsPage.stream().filter(feedItemId -> {
			Optional<FeedItemUser> opitonalFeedItemUser = recentlyReadFeedItemUserRepository
					.findByIdAndLastReadingDateNotNull(new FeedItemUserId(feedItemId, userId));
			return opitonalFeedItemUser.isPresent();
		}).map(feedItemId -> new FeedItemUserId(feedItemId, userId)).collect(Collectors.toList());

		List<FeedItemUser> feedItemUsers = feedItemUsersIds.stream().map(feedItemUsersId -> {
			Optional<FeedItemUser> opitonalFeedItemUser = recentlyReadFeedItemUserRepository
					.findByIdAndLastReadingDateNotNull(feedItemUsersId);
			return opitonalFeedItemUser.get();
		}).collect(Collectors.toList());

		return feedItemUsers;
	}

}
