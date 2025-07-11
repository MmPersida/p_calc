package com.persida.pathogenicity_calculator.services.userServices;

import com.persida.pathogenicity_calculator.dto.BasicUserDataDto;
import com.persida.pathogenicity_calculator.repository.CustomUserDetails;
import com.persida.pathogenicity_calculator.repository.entity.User;
import org.springframework.security.core.Authentication;

public interface UserService {
    CustomUserDetails loadCustomUserDetailsByUsername(String username);
    public boolean saveNewUser(User user);
    public String getCurrentUserFullName();
    public User getUserByUsername(String username);
    public Integer getCurrentUserId();
    public User getUserById(Integer id);
    public BasicUserDataDto getBacisUserDataByUsername(String username);
}
