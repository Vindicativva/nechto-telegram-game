package ru.nechto.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.nechto.repository.jpa.RoomJpaRepository;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Модульные тесты генератора кодов комнат и ссылок-приглашений.
 */
@ExtendWith(MockitoExtension.class)
class RoomCodeGeneratorTest {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9]{6}$");

    @Mock
    private RoomJpaRepository roomJpaRepository;

    /**
     * Проверяет, что сгенерированный код комнаты имеет ровно 6 символов и состоит
     * только из допустимых символов алфавита (A-Z, 0-9).
     *
     * <p><b>Предусловия (Given):</b> Инициализированный генератор кодов и репозиторий,
     * сообщающий, что сгенерированный код свободен (коллизий нет).</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code generateCode()}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Код не равен {@code null}.</li>
     *   <li>Длина кода строго равна 6 символам.</li>
     *   <li>Код полностью соответствует шаблону {@code ^[A-Z0-9]{6}$}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Генерация кода комнаты: длина 6 символов, только A-Z и 0-9")
    void generateCode_ValidLengthAndCharacters() {
        when(roomJpaRepository.existsByCode(anyString())).thenReturn(false);
        RoomCodeGenerator generator = new RoomCodeGenerator(roomJpaRepository, "StayAwayGameBot");

        String code = generator.generateCode();

        assertThat(code).isNotNull();
        assertThat(code).hasSize(6);
        assertThat(CODE_PATTERN.matcher(code).matches()).isTrue();
    }

    /**
     * Проверяет механизм повторной генерации кода при возникновении коллизии
     * с уже существующим в базе данных кодом комнаты.
     *
     * <p><b>Предусловия (Given):</b> Репозиторий при первой проверке {@code existsByCode}
     * возвращает {@code true} (коллизия), а при второй — {@code false} (уникальный код).</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code generateCode()}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Генератор выполняет повторный запрос к репозиторию.</li>
     *   <li>Возвращается валидный уникальный код.</li>
     *   <li>Репозиторий был вызван как минимум 2 раза.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Генерация кода комнаты: повторная попытка при коллизии кодов в БД")
    void generateCode_RetriesOnCollision() {
        when(roomJpaRepository.existsByCode(anyString()))
                .thenReturn(true)
                .thenReturn(false);

        RoomCodeGenerator generator = new RoomCodeGenerator(roomJpaRepository, "StayAwayGameBot");

        String code = generator.generateCode();

        assertThat(code).isNotNull();
        assertThat(code).hasSize(6);
        verify(roomJpaRepository, atLeast(2)).existsByCode(anyString());
    }

    /**
     * Проверяет корректность формирования ссылки-приглашения Telegram Mini App.
     *
     * <p><b>Предусловия (Given):</b> Код комнаты {@code "X7K9M2"} и имя бота {@code "StayAwayGameBot"}.</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code generateInviteLink("X7K9M2")}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Возвращается строка вида {@code https://t.me/StayAwayGameBot/play?startapp=X7K9M2}.
     * </p>
     */
    @Test
    @DisplayName("Формирование inviteLink: корректный диплинк Telegram Mini App")
    void generateInviteLink_FormatsCorrectLink() {
        RoomCodeGenerator generator = new RoomCodeGenerator(roomJpaRepository, "StayAwayGameBot");

        String inviteLink = generator.generateInviteLink("X7K9M2");

        assertThat(inviteLink).isEqualTo("https://t.me/StayAwayGameBot/app?startapp=X7K9M2");
    }
}
