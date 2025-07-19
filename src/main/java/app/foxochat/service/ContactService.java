package app.foxochat.service;

import app.foxochat.model.UserContact;

import java.util.List;

public interface ContactService {

    List<UserContact> findAllByUserId(long id);
}
