package org.ddamme.service;

import org.ddamme.database.model.User;
import org.ddamme.dto.RegisterRequest;

public interface UserService {
    User registerUser(RegisterRequest request);
} 