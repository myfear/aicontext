package com.aicontext.maven.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GraphNotationParserTest {

    @Test
    void parseBlock_singleNodeWithEdges() {
        String block = "PaymentService\n"
                + "  ├─[uses]→ StripeClient, PaymentRepository, EventPublisher\n"
                + "  ├─[calls]→ StripeClient.charge(), PaymentRepository.save()\n"
                + "  └─[by]← OrderService.checkout(), SubscriptionService.charge()";
        GraphNode node = GraphNotationParser.parseBlock(block);
        assertThat(node.getName()).isEqualTo("PaymentService");
        assertThat(node.getEdges()).hasSize(3);
        assertThat(node.getEdges().get(0).getRelationType()).isEqualTo("uses");
        assertThat(node.getEdges().get(0).getDirection()).isEqualTo(GraphEdge.Direction.OUTBOUND);
        assertThat(node.getEdges().get(0).getTargets()).containsExactly("StripeClient", "PaymentRepository", "EventPublisher");
        assertThat(node.getEdges().get(2).getRelationType()).isEqualTo("by");
        assertThat(node.getEdges().get(2).getDirection()).isEqualTo(GraphEdge.Direction.INBOUND);
        assertThat(node.getEdges().get(2).getTargets()).containsExactly("OrderService.checkout()", "SubscriptionService.charge()");
    }

    @Test
    void parseBlock_externalAndConfig() {
        String block = "StripeClient\n"
                + "  ├─[external]→ https://api.stripe.com/v1\n"
                + "  ├─[config]→ STRIPE_API_KEY\n"
                + "  └─[by]← PaymentService, RefundService";
        GraphNode node = GraphNotationParser.parseBlock(block);
        assertThat(node.getName()).isEqualTo("StripeClient");
        assertThat(node.getEdges()).hasSize(3);
        assertThat(node.getEdges().get(0).getRelationType()).isEqualTo("external");
        assertThat(node.getEdges().get(0).getTargets()).containsExactly("https://api.stripe.com/v1");
        assertThat(node.getEdges().get(1).getRelationType()).isEqualTo("config");
    }

    @Test
    void parseBlock_dbAndEvents() {
        String block = "PaymentService\n"
                + "  ├─[db]→ W:payment_transactions(id,user_id,amount,currency,status)\n"
                + "  ├─[events]→ PaymentProcessedEvent";
        GraphNode node = GraphNotationParser.parseBlock(block);
        assertThat(node.getName()).isEqualTo("PaymentService");
        assertThat(node.getEdges().get(0).getRelationType()).isEqualTo("db");
        assertThat(node.getEdges().get(0).getTargets()).hasSize(1);
        assertThat(node.getEdges().get(0).getTargets().get(0)).isEqualTo("W:payment_transactions(id,user_id,amount,currency,status)");
        assertThat(node.getEdges().get(1).getRelationType()).isEqualTo("events");
    }

    @Test
    void parseBlock_emptyOrBlank() {
        assertThat(GraphNotationParser.parseBlock("").getName()).isEmpty();
        assertThat(GraphNotationParser.parseBlock("   \n  ").getName()).isEmpty();
        assertThat(GraphNotationParser.parseBlock(null).getName()).isEmpty();
    }

    @Test
    void parseBlock_javadocPrefix_stripsAsteriskAndParsesEdge() {
        // Content as extracted from Javadoc (leading " * " on each line)
        String block = " *                  GreetingResource\n"
                + " *                  └─[uses]→ GreetService";
        GraphNode node = GraphNotationParser.parseBlock(block);
        assertThat(node.getName()).isEqualTo("GreetingResource");
        assertThat(node.getEdges()).hasSize(1);
        assertThat(node.getEdges().get(0).getRelationType()).isEqualTo("uses");
        assertThat(node.getEdges().get(0).getTargets()).containsExactly("GreetService");
        // Validation uses getDocumentedUses; when class is GreetResource we fall back to first node
        Set<String> uses = GraphNotationParser.getDocumentedUses(block, "GreetResource");
        assertThat(uses).containsExactly("GreetService");
    }

    @Test
    void parseBlocks_multipleNodes() {
        String content = "PaymentService\n"
                + "  ├─[uses]→ StripeClient\n"
                + "  └─[by]← OrderService\n"
                + "\n"
                + "StripeClient\n"
                + "  ├─[external]→ https://api.stripe.com\n"
                + "  └─[by]← PaymentService";
        List<GraphNode> nodes = GraphNotationParser.parseBlocks(content);
        assertThat(nodes).hasSize(2);
        assertThat(nodes.get(0).getName()).isEqualTo("PaymentService");
        assertThat(nodes.get(0).getEdges()).hasSize(2);
        assertThat(nodes.get(1).getName()).isEqualTo("StripeClient");
        assertThat(nodes.get(1).getEdges()).hasSize(2);
    }
}
