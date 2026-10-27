# Publicar o Notilog no Google Play

Passo a passo no [Play Console](https://play.google.com/console), com a conta de desenvolvedor.
Tudo o que precisa ser enviado está nesta pasta (`play-store/`).

## 1. Gerar o arquivo de envio

```bash
./gradlew :NLSExample:bundleRelease
```

Arquivo: `NLSExample/build/outputs/bundle/release/NLSExample-release.aab`

Ele é assinado com a **chave de upload** em `~/keystores/notilog-upload.jks`; a senha está em
`keystore.properties` na raiz do projeto (fora do Git). **Faça backup dos dois** num lugar seguro
(pendrive, gerenciador de senhas). Sem eles, é preciso pedir ao Google para trocar a chave.

A cada nova versão, aumente `versionCode` (e `versionName`) em `NLSExample/build.gradle`.

## 2. Criar o app

*Todos os apps › Criar app*

| Campo | Valor |
|---|---|
| Nome do app | Notilog: Notification History |
| Idioma padrão | Inglês (Estados Unidos) – en-US |
| App ou jogo | App |
| Gratuito ou pago | Gratuito |

Aceite as declarações (políticas do programa e leis de exportação dos EUA).

## 3. Configurar o app (*Painel › Configurar o app*)

| Formulário | Resposta |
|---|---|
| **Política de privacidade** | `https://github.com/moises-dev-git/NotificationListenerService-Example/blob/master/PRIVACY.md` |
| **Acesso ao app** | Todas as funcionalidades disponíveis sem restrições (não há login). |
| **Anúncios** | Não, o app não contém anúncios. |
| **Classificação de conteúdo** | Categoria *Utilitário, produtividade, comunicação ou outro*. Responda **Não** a tudo (violência, sexo, drogas, apostas, interação entre usuários, compartilhamento de localização, compras). |
| **Público-alvo** | **18 anos ou mais**. O app mostra o conteúdo de mensagens; marcar faixas menores ativa as regras da política Famílias. |
| **App de notícias** | Não. |
| **Apps de saúde / governamentais / recursos financeiros** | Não / Não / Nenhum recurso financeiro. |
| **Segurança dos dados** | Ver seção 4. |
| **Categoria** | Ferramentas (Tools). |
| **Detalhes de contato** | E-mail: djxgauss@gmail.com (fica público na página do app). |

## 4. Segurança dos dados

- *Seu app coleta ou compartilha algum dos tipos de dados do usuário obrigatórios?* → **Não**.

Motivo: dados processados apenas no aparelho, que nunca saem dele, não contam como "coletados"
para o Google Play. O Notilog não tem permissão de internet.

## 5. Página da loja (*Crescimento › Presença na loja › Página principal da loja*)

Inglês (en-US): textos em `listing-en-US.md`. Adicione a tradução **Português (Brasil) – pt-BR**
com os textos de `listing-pt-BR.md`.

| Recurso | Arquivo |
|---|---|
| Ícone (512×512) | `icon-512.png` |
| Gráfico de recursos (1024×500) | `feature-graphic-en.png` / `feature-graphic-pt.png` |
| Capturas de tela do telefone | `screenshots/en-US/` / `screenshots/pt-BR/` (na ordem 1 a 4) |

## 6. Enviar e publicar

1. *Testar e lançar › Produção › Criar nova versão*.
2. Aceite o **Assinatura de apps do Google Play** (o Google guarda a chave final; a sua é só de upload).
3. Envie o `.aab`. Notas da versão:
   - en-US: `First release.`
   - pt-BR: `Primeira versão.`
4. *Países/regiões*: escolha onde publicar (ex.: todos).
5. *Revisar versão › Iniciar lançamento na produção*.

A primeira revisão costuma levar de alguns dias a uma semana. Apps que leem notificações recebem
análise mais cuidadosa; se o Google pedir esclarecimentos, a resposta é: o app guarda o histórico
das notificações **somente no aparelho**, para o próprio usuário consultar, sem enviar nada a
servidores.

## Atenção: app instalado hoje × app da loja

O app instalado no celular hoje é a versão de **depuração**, assinada com outra chave. O Android
não deixa instalar a versão da loja por cima dela: será preciso **desinstalar a atual**, o que apaga
o histórico e os áudios. Antes disso, faça backup (Ajustes › Salvar JSON para o histórico; os áudios
podem ser copiados por cabo/adb).
