package com.vogella.spring.datacrawler.services;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.daweiser.keywordextractor.Keyword;
import com.daweiser.keywordextractor.KeywordExtractor;
import com.vogella.spring.data.entities.RankedBug;
import com.vogella.spring.datacrawler.entities.Bug;
import com.vogella.spring.datacrawler.issueextractor.dto.BugDto;
import com.vogella.spring.datacrawler.issueextractor.dto.CommentDto;
import com.vogella.spring.datacrawler.repositories.BugRepository;

@Service("bugService")
public class BugService {

	@Autowired
	KeywordExtractor keywordExtractor;

	@Autowired
	private BugRepository bugRepository;

	public Map<String, Integer> findAllKeywordsForUser(String user) {
		List<Bug> bugs = bugRepository.findByAssignedTo(user);
		Map<String, Integer> keywords = new HashMap<String, Integer>();
		bugs.forEach(bug -> {
			bug.getKeywords().forEach((k, newFrequency) -> {
				if (keywords.containsKey(k)) {
					keywords.put(k, keywords.get(k) + newFrequency);
				} else {
					keywords.put(k, newFrequency);
				}
			});
		});
		return keywords;
	}

	public void saveBugDtos(List<BugDto> bugDtos) {
		ArrayList<Bug> bugs = new ArrayList<>();
		bugDtos.forEach(bugdto -> {
			Bug bug = new Bug();
			bug.setBugIdBugzilla(bugdto.getBugDtoId());
			bug.setTitle(bugdto.getTitle());
			String bugTitle = bugdto.getTitle().replaceAll("[\\p{Punct}&&[^\\.]]+", " ").replaceAll("[0-9]", " ");
			bug.setKeywords(getKeywordMap(keywordExtractor.doKeywordExtraction(bugTitle)));
			// bug.setDescription(getBugDescriptionFromComments(bugdto));
			bug.setClassification(bugdto.getClassification());
			bug.setProduct(bugdto.getProduct());
			bug.setComponent(bugdto.getComponent());
			bug.setReporter(bugdto.getReporter());
			bug.setAssignedTo(bugdto.getAssignedTo());
			bug.setCreationTimestamp(convertTimestampToMillis(bugdto.getCreationTimestamp()));
			bug.setLastChangeTimestamp(convertTimestampToMillis(bugdto.getLastChangeTimestamp()));
			bug.setResolution(bugdto.getResolution());
			bug.setPriority(bugdto.getPriority());
			bug.setSeverity(bugdto.getSeverity());
			bug.setStatus(bugdto.getStatus());
			bug.setVersion(bugdto.getVersion());
			bug.setReportedPlatform(bugdto.getReportedPlatform());
			bug.setOperationSystem(bugdto.getOperationSystem());
			bug.setMilestone(bugdto.getMilestone());
			bug.setVotes(bugdto.getVotes());
			bug.setCountAttachments(bugdto.getAttachmentDtos().size());
			bug.setCountBlocks(bugdto.getBlocks().size());
			bug.setCountCC(bugdto.getCcList().size());
			bug.setCountComments(getCountComments(bugdto.getCommentDtos()));
			bug.setCountDependsOn(bugdto.getDependsOn().size());
			bug.setCountDuplicates(getCountDuplicates(bugdto.getCommentDtos()));
			bug.setCountAdditionalLinks(getCountAdditionalLinks(bugdto.getAdditionalLinks()));
			bugs.add(bug);
		});
		bugRepository.save(bugs);
	}

	public List<RankedBug> getRankedIssues() {
		List<RankedBug> rankedBugs = new ArrayList<>();
		bugRepository.findAll().forEach(bug -> {
			RankedBug rankedBug = new RankedBug();
			rankedBug.setBugIdBugzilla(bug.getBugIdBugzilla());
			rankedBug.setAssignedTo(bug.getAssignedTo());
			rankedBug.setComponent(bug.getComponent());
			rankedBug.setPriority(bug.getPriority());
			rankedBug.setReporter(bug.getReporter());
			rankedBug.setSeverity(bug.getSeverity());
			rankedBug.setStatus(bug.getStatus());
			rankedBug.setTitle(bug.getTitle());
			rankedBug.setVotes(bug.getVotes());
			rankedBug.setCreated(convertTimestampToString(bug.getCreationTimestamp()));
			rankedBug.setLastChanged(convertTimestampToString(bug.getLastChangeTimestamp()));
			rankedBug.setCountAttachments(bug.getCountAttachments());
			rankedBug.setCountBlocks(bug.getCountBlocks());
			rankedBug.setCountCC(bug.getCountCC());
			rankedBug.setCountDependsOn(bug.getCountDependsOn());
			rankedBug.setCountDuplicates(bug.getCountDuplicates());
			rankedBugs.add(rankedBug);
		});

		return rankedBugs;
	}

	private String convertTimestampToString(long millis) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(millis);
		return sdf.format(new Date(calendar.getTimeInMillis()));
	}

	private Map<String, Integer> getKeywordMap(Set<Keyword> keywords) {
		Map<String, Integer> map = new HashMap<String, Integer>();
		for (Keyword keyword : keywords) {
			map.put(keyword.getLemma(), keyword.getFrequency());
		}
		return map;
	}

	private int getCountDuplicates(List<CommentDto> commentDtos) {
		// hacky method to get the number of duplicates for a bug report
		int count = 0;
		for (CommentDto c : commentDtos) {
			String text = c.getText();
			if (text != null) {
				if (text.contains("has been marked as a duplicate of this bug")) {
					// this is the standard text used if a bug is marked as duplicate
					count++;
				}
			}
		}
		return count;
	}

	private int getCountAdditionalLinks(Set<String> addLinks) {
		int count = 0;
		for (String link : addLinks) {
			if (!link.contains("https://git.eclipse.org/r/") && !link.contains("https://git.eclipse.org/c/")) {
				count++;
			}
		}
		return count;
	}

	private int getCountComments(List<CommentDto> comments) {
		int count = 0;
		for (CommentDto comment : comments) {
			String text = comment.getText();
			if (!text.contains("has been marked as a duplicate of this bug")
					&& !text.contains("New Gerrit change created:") && !text.contains("was merged to [master]")) {
				count++;
			}
		}
		return count;
	}

	private long convertTimestampToMillis(String dateString) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
		Calendar calendar = Calendar.getInstance();
		try {
			calendar.setTime(sdf.parse(dateString));
			return calendar.getTimeInMillis();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return 0l;
	}

	private String getBugDescriptionFromComments(BugDto bugDto) {
		CommentDto commentDto = bugDto.getCommentDtos().get(0);
		if (commentDto.getCommentCount() == 0 && commentDto.getAuthor().equals(bugDto.getReporter())) {
			return commentDto.getText();
		}
		return "";
	}
}
