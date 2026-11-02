package com.clerova.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostmarkInboundParserTest {

    @Test
    void htmlOnlyEmailsBecomeReadableText() {
        String html = "<p>Hi, the sink&#8217;s leaking &amp; the floor&rsquo;s wet.</p>"
                + "<p>Unit&nbsp;4B &#x2014; see &amp;lt;photo&amp;gt;</p><style>p{}</style>";

        assertThat(PostmarkInboundParser.htmlToText(html))
                .isEqualTo("Hi, the sink’s leaking & the floor’s wet.\nUnit 4B — see &lt;photo&gt;");
    }

    @Test
    void unknownOrBrokenEntitiesAreLeftAsTheyAre() {
        assertThat(PostmarkInboundParser.decodeEntities("A &bogus; B &#99999999; C &#0;"))
                .isEqualTo("A &bogus; B &#99999999; C &#0;");
    }

    @Test
    void readsASingleReplyToAddress() {
        assertThat(PostmarkInboundParser.address("Tina Alvarez <Tina@Example.com>"))
                .containsExactly("tina@example.com", "Tina Alvarez");
        assertThat(PostmarkInboundParser.address("\"Alvarez, Tina\" <tina@example.com>"))
                .containsExactly("tina@example.com", "Alvarez, Tina");
        assertThat(PostmarkInboundParser.address("tina@example.com"))
                .containsExactly("tina@example.com", null);
        // Several addresses: no one person to answer
        assertThat(PostmarkInboundParser.address("a@x.com, b@x.com")).containsExactly(null, null);
        assertThat(PostmarkInboundParser.address("not an address")).containsExactly(null, null);
        assertThat(PostmarkInboundParser.address(null)).containsExactly(null, null);
    }
}
