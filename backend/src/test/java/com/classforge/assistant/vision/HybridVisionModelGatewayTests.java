package com.classforge.assistant.vision;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridVisionModelGatewayTests {

    @Test
    void returnsSemanticProposalWithoutInvokingHybridWhenDisabled() {
        VisionUmlProposal semantic = proposal(4);
        AtomicInteger detectorCalls = new AtomicInteger();

        VisionUmlProposal result = gateway(semantic, false, 4, true, detectorCalls).analyze(null, null);

        assertEquals(semantic, result);
        assertEquals(0, detectorCalls.get());
    }

    @Test
    void returnsSemanticProposalBelowDenseThreshold() {
        VisionUmlProposal semantic = proposal(3);
        AtomicInteger detectorCalls = new AtomicInteger();

        VisionUmlProposal result = gateway(semantic, true, 4, true, detectorCalls).analyze(null, null);

        assertEquals(semantic, result);
        assertEquals(0, detectorCalls.get());
    }

    @Test
    void entersHybridAtInclusiveThreshold() {
        AtomicInteger detectorCalls = new AtomicInteger();

        VisionUmlProposal result = gateway(proposal(4), true, 4, true, detectorCalls).analyze(null, null);

        assertEquals(1, detectorCalls.get());
        assertTrue(result.safeWarnings().stream().anyMatch(warning -> warning.startsWith("HYBRID_CV_FALLBACK:")));
    }

    @Test
    void entersHybridAboveDenseThreshold() {
        AtomicInteger detectorCalls = new AtomicInteger();

        assertThrows(
                VisionModelGatewayException.class,
                () -> gateway(proposal(5), true, 4, false, detectorCalls).analyze(null, null)
        );

        assertEquals(1, detectorCalls.get());
    }

    @Test
    void fallsBackToSemanticWhenHybridFailsAndFallbackIsEnabled() {
        VisionUmlProposal semantic = proposal(4);

        VisionUmlProposal result = gateway(semantic, true, 4, true, new AtomicInteger()).analyze(null, null);

        assertEquals(semantic.safeClasses(), result.safeClasses());
        assertTrue(result.safeWarnings().stream().anyMatch(warning -> warning.startsWith("HYBRID_CV_FALLBACK:")));
    }

    @Test
    void propagatesHybridFailureWhenFallbackIsDisabled() {
        VisionModelGatewayException exception = assertThrows(
                VisionModelGatewayException.class,
                () -> gateway(proposal(4), true, 4, false, new AtomicInteger()).analyze(null, null)
        );

        assertEquals(VisionModelGatewayException.Reason.OUTPUT_CONTRACT, exception.reason());
    }

    private HybridVisionModelGateway gateway(
            VisionUmlProposal semantic,
            boolean enabled,
            int minClasses,
            boolean fallbackToSemantic,
            AtomicInteger detectorCalls
    ) {
        LlamaCppVisionModelGateway llama = mock(LlamaCppVisionModelGateway.class);
        when(llama.analyze(any(), any())).thenReturn(semantic);
        @SuppressWarnings("unchecked")
        ObjectProvider<LlamaCppVisionModelGateway> llamaProvider = mock(ObjectProvider.class);
        when(llamaProvider.getIfAvailable()).thenReturn(llama);
        @SuppressWarnings("unchecked")
        ObjectProvider<UnconfiguredVisionModelGateway> unconfiguredProvider = mock(ObjectProvider.class);
        UmlClassRegionDetector detector = (image, expectedClassCount) -> {
            detectorCalls.incrementAndGet();
            throw new IllegalStateException("hybrid detector reached");
        };
        return new HybridVisionModelGateway(
                llamaProvider, unconfiguredProvider, null, detector, null, null, null, null, null,
                enabled, minClasses, fallbackToSemantic
        );
    }

    private VisionUmlProposal proposal(int classes) {
        return new VisionUmlProposal(
                "semantic", java.util.stream.IntStream.range(0, classes)
                .mapToObj(index -> new VisionClassProposal("c" + index, "Class" + index, List.of(), null))
                .toList(),
                List.of(), List.of(), 0.9
        );
    }
}
