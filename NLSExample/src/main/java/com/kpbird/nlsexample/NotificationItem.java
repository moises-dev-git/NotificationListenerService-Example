package com.kpbird.nlsexample;

/**
 * Representa uma notificação já processada,
 * separando informações como horário, aplicativo,
 * título e corpo da mensagem.
 */
public class NotificationItem {

    // Id da linha no banco (NotificationDb); -1 se não veio do banco
    public long id = -1;

    // Horário da notificação (ex: 14:35:10)
    public String time;

    // Nome do aplicativo que gerou a notificação
    public String appName;

    // Título da notificação
    public String title;

    // Conteúdo da notificação
    public String body;

    // Texto bruto recebido antes do processamento
    public String rawText;

    /**
     * Construtor que recebe o texto bruto da notificação
     * e tenta extrair suas partes.
     */
    public NotificationItem(String rawText) {

        // Armazena o texto original
        this.rawText = rawText;

        try {

            // Procura o caractere "]"
            // Exemplo:
            // [14:35:10] App: WhatsApp | João: Olá!
            int bracketEnd = rawText.indexOf("]");

            // Verifica se existe "]" e se a string começa com "["
            if (bracketEnd != -1 && rawText.startsWith("[")) {

                // Extrai o horário entre os colchetes
                // Resultado: "14:35:10"
                this.time = rawText.substring(1, bracketEnd);

                // Remove a parte "[14:35:10] "
                // Resultado:
                // "App: WhatsApp | João: Olá!"
                String rest = rawText.substring(bracketEnd + 2);

                // Procura o início do nome do aplicativo
                int appStart = rest.indexOf("App: ");

                // Procura o separador " | "
                int pipeIndex = rest.indexOf(" | ");

                // Se encontrou ambos os marcadores...
                if (appStart != -1 && pipeIndex != -1) {

                    // Extrai o nome do aplicativo
                    // Exemplo:
                    // "WhatsApp"
                    this.appName = rest.substring(appStart + 5, pipeIndex);

                    // Pega o restante após " | "
                    // Exemplo:
                    // "João: Olá!"
                    String payload = rest.substring(pipeIndex + 3);

                    // Procura o separador entre título e corpo
                    int colonIndex = payload.indexOf(": ");

                    // Se encontrou ": "
                    if (colonIndex != -1) {

                        // Extrai o título
                        // Resultado: "João"
                        this.title = payload.substring(0, colonIndex);

                        // Extrai o conteúdo da mensagem
                        // Resultado: "Olá!"
                        this.body = payload.substring(colonIndex + 2);

                    } else {

                        // Caso não exista título,
                        // deixa o título vazio
                        this.title = "";

                        // Todo o conteúdo vira corpo da mensagem
                        this.body = payload;
                    }

                } else {

                    // Caso o formato esperado não seja encontrado,
                    // salva o texto restante como corpo
                    this.body = rest;
                }

            } else {

                // Caso não exista o horário entre colchetes,
                // considera toda a string como corpo
                this.body = rawText;
            }

        } catch (Exception e) {

            // Se qualquer erro ocorrer durante o parsing,
            // evita que o aplicativo trave e salva o texto bruto
            this.body = rawText;
        }
    }
}