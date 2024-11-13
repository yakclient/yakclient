package com.example;

import com.durganmcbroom.jobs.EmptyJobContext;
import com.durganmcbroom.jobs.Job;
import dev.extframework.tooling.api.environment.ExtensionEnvironment;
import dev.extframework.tooling.api.extension.ExtensionNodeObserver;
import dev.extframework.tooling.api.tweaker.EnvironmentTweaker;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import static com.durganmcbroom.jobs.Builders.job;

public class TestTweaker implements EnvironmentTweaker {
    @NotNull
    @Override
    public Job<Unit> tweak(@NotNull ExtensionEnvironment environment) {
        return job(EmptyJobContext.INSTANCE, (scope) -> {
            System.out.println("Welp, made it to here");

            environment.plusAssign((ExtensionNodeObserver) node -> System.out.println("Observing node: " + node.getDescriptor()));

            return Unit.INSTANCE;
        });
    }
}