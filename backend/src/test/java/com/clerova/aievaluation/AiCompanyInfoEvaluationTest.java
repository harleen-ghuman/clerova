package com.clerova.aievaluation;

import com.clerova.ai.AiService;
import com.clerova.domain.Category;
import com.clerova.domain.Priority;
import com.clerova.domain.WorkItem;
import com.clerova.domain.WorkItemStatus;
import com.clerova.dto.CompanyInfo;
import com.clerova.dto.ProposedActionResult;
import com.clerova.service.CompanyInfoService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Suggested replies answer from the company's own information, and only from it. */
@SpringBootTest
@Tag("ai-evaluation")
class AiCompanyInfoEvaluationTest {

    private static final String INFO = CompanyInfoService.asText(new CompanyInfo(
            "Rent is due on the 1st. Pay in the resident portal at pay.mapleproperty.com. "
                    + "There is a 5-day grace period; after that the late fee is $50.",
            "Office hours are Monday to Friday, 9am to 5pm. Call (555) 201-3300.",
            "For emergencies after hours, call (555) 201-3399. Emergencies are fire, gas, flooding, "
                    + "no heat in winter and lockouts.",
            null, null,
            "Cats and dogs under 40 lb are allowed with a $300 pet deposit. Two pets at most.",
            "Each unit has one assigned spot. Guests park on the street.",
            null, null, null, null, null, null
    ));

    @Autowired
    private AiService aiService;

    private static WorkItem item(Category category, String name, String message) {
        WorkItem workItem = new WorkItem();
        workItem.setOrganizationId(UUID.randomUUID());
        workItem.setStatus(WorkItemStatus.NEW);
        workItem.setSenderName(name);
        workItem.setCategory(category);
        workItem.setPriority(Priority.LOW);
        workItem.setAddress("742 Oak St, Unit 3A");
        workItem.setSummary(message);
        workItem.setOriginalMessage(message);
        return workItem;
    }

    @Test
    void answersALateFeeQuestionFromTheCompanyInfo() {
        ProposedActionResult result = aiService.proposeAction(
                item(Category.BILLING, "Dan Ortiz", "If I pay rent on the 4th, will I get a late fee?"),
                null,
                INFO
        );

        String reply = result.draftedResponse.toLowerCase();
        assertThat(reply).contains("5").contains("$50");
        assertThat(reply).doesNotContain("clerova", "approv", "pending");
    }

    @Test
    void answersAPetQuestionWithTheExactPolicy() {
        ProposedActionResult result = aiService.proposeAction(
                item(Category.GENERAL, "Leah Kim", "Can I get a dog? She's a 25 lb beagle."),
                null,
                INFO
        );

        assertThat(result.draftedResponse).contains("$300");
    }

    @Test
    void doesNotMakeUpAPolicyThatIsMissing() {
        // Nothing about smoking in the company info
        ProposedActionResult result = aiService.proposeAction(
                item(Category.GENERAL, "Sam Rivera", "Is smoking allowed on my balcony?"),
                null,
                INFO
        );

        String reply = result.draftedResponse.toLowerCase();
        assertThat(reply).containsAnyOf("check", "confirm", "follow up", "get back");
        assertThat(reply).doesNotContain(
                "smoking is allowed", "smoking is not allowed", "smoking is prohibited",
                "avoid smoking", "please don't smoke", "please do not smoke");
    }

    @Test
    void stillSendsTheResidentsOwnBalanceToTheTeam() {
        ProposedActionResult result = aiService.proposeAction(
                item(Category.BILLING, "Marcus Lee", "Why is my balance $95 higher this month?"),
                null,
                INFO
        );

        String reply = result.draftedResponse.toLowerCase();
        assertThat(reply).containsAnyOf("check", "look into", "review", "follow up");
        assertThat(reply).doesNotContain("because of the late fee", "is the late fee");
    }

    @Test
    void draftDuringAJobUsesTheEmergencyNumber() {
        WorkItem workItem = item(Category.MAINTENANCE, "Tina Alvarez", "Heater not working.");
        workItem.setStatus(WorkItemStatus.IN_PROGRESS);

        String reply = aiService.draftReply(
                workItem,
                "Message from Tina Alvarez <tina@x.com> at 2026-10-26T21:00:00Z:\n"
                        + "The heater is still out and it's freezing tonight. Who do I call after hours?",
                INFO
        );

        assertThat(reply).contains("201-3399");
    }
}
