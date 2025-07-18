package app.foxochat.service;

import app.foxochat.model.Channel;
import app.foxochat.model.Member;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface MemberService {
    List<Channel> getChannelsByUserId(long userId);

    List<Member> getAllByChannelId(long channelId);

    CompletableFuture<Optional<Member>> getByChannelIdAndUserId(long channelId, long userId);

    void add(Member member);

    void delete(Member member);
}
