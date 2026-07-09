package ru.neoflex.rag.model.entity;

public enum AnswerStyle {
    EXPERT,
    SIMPLE,
    ELI5;

    /**
     * Определяет стиль по суффиксу модели
     * Например: glm-4.7:cloud-eli5 = ELI5
     *          glm-4.7:cloud-simple = SIMPLE
     *          glm-4.7:cloud = EXPERT (по умолчанию)
     */
    public static AnswerStyle fromModel(String model) {
        if (model == null) {
            return EXPERT;
        }

        String lower = model.toLowerCase();
        if (lower.contains("-eli5")) {
            return ELI5;
        } else if (lower.contains("-simple")) {
            return SIMPLE;
        }
        return EXPERT;
    }
}
