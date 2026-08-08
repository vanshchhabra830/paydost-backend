package com.example.paydost.integration;

import com.example.paydost.dto.AddMoneyRequestDto;
import com.example.paydost.dto.LoginRequestDto;
import com.example.paydost.dto.RegisterRequestDto;
import com.example.paydost.dto.TransferRequestDto;
import com.example.paydost.repository.UserRepository;
import com.example.paydost.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test") // Loads application-test.properties
class PayDostIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate; // Mock Redis

    @MockBean
    private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private ValueOperations<String, Object> valueOperations;

    @BeforeEach
    void setUp() {
        // Clear DB before each test
        walletRepository.deleteAll();
        userRepository.deleteAll();
        
        // Mock Redis behavior for RateLimitingService and TransactionService
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
    }

    @Test
    void fullTransferFlow_Successful() throws Exception {
        // 1. Register Alice
        RegisterRequestDto aliceReg = RegisterRequestDto.builder()
                .fullName("Alice")
                .email("alice@example.com")
                .password("secret123")
                .build();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aliceReg)))
                .andExpect(status().isCreated());

        // 2. Register Bob
        RegisterRequestDto bobReg = RegisterRequestDto.builder()
                .fullName("Bob")
                .email("bob@example.com")
                .password("secret456")
                .build();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bobReg)))
                .andExpect(status().isCreated());

        // 3. Login as Alice to get token
        LoginRequestDto aliceLogin = LoginRequestDto.builder()
                .email("alice@example.com")
                .password("secret123")
                .build();
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aliceLogin)))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseContent = loginResult.getResponse().getContentAsString();
        String aliceToken = objectMapper.readTree(responseContent).get("token").asText();

        // 4. Add money to Alice's wallet
        AddMoneyRequestDto addMoney = AddMoneyRequestDto.builder()
                .amount(new BigDecimal("1000.00"))
                .build();
        mockMvc.perform(post("/api/wallet/add-money")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addMoney)))
                .andExpect(status().isOk());

        // 5. Transfer to Bob
        TransferRequestDto transfer = TransferRequestDto.builder()
                .receiverEmail("bob@example.com")
                .amount(new BigDecimal("300.00"))
                .referenceId("test-uuid-12345")
                .build();
        mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transfer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("300.0"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
}
