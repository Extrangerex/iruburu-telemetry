# Iruburu Server Telemetry Mod Spec

Este mod es server-side. Su unica responsabilidad es escuchar eventos confiables del servidor Minecraft/Forge y reportarlos al API de Iruburu.

## Objetivo

Enviar eventos de servidor a:

```txt
POST {apiBaseUrl}/api/v1/telemetry/server/events
X-Telemetry-API-Key: {apiKey}
Content-Type: application/json
```

El endpoint ya existe en el backend Iruburu.

## Configuracion

El mod debe leer su configuracion desde system properties de Java y, opcionalmente, desde un archivo TOML de Forge.

Propiedades JVM minimas:

```txt
iruburu.telemetry.apiUrl
iruburu.telemetry.apiKey
iruburu.telemetry.serverId
iruburu.telemetry.serverName
iruburu.telemetry.packId
iruburu.telemetry.packVersion
```

Ejemplo de arranque:

```sh
java \
  -Diruburu.telemetry.apiUrl=http://localhost:8080 \
  -Diruburu.telemetry.apiKey=change-this-local-telemetry-key \
  -Diruburu.telemetry.serverId=survival-01 \
  -Diruburu.telemetry.serverName="Survival 01" \
  -Diruburu.telemetry.packId=survival \
  -Diruburu.telemetry.packVersion=1.0.0 \
  -jar forge-server.jar nogui
```

Si `apiKey` esta vacio, el mod no debe enviar nada. Debe registrar un warning una sola vez y seguir funcionando.

## Metadata fija

Cada evento debe incluir:

```json
{
  "serverId": "survival-01",
  "serverName": "Survival 01",
  "packId": "survival",
  "packVersion": "1.0.0",
  "minecraftVersion": "1.20.1",
  "loader": "forge"
}
```

`minecraftVersion` puede salir de `SharedConstants.getCurrentVersion().getName()` o equivalente disponible en mappings oficiales.

## Eventos requeridos

El mod debe emitir exactamente estos `eventType`, porque el API valida la lista:

```txt
player_connected
player_disconnected
player_death
player_damaged
dimension_changed
inventory_snapshot
advancement_completed
```

## Payload base por jugador

Cuando el evento tenga jugador, incluir:

```json
{
  "playerUuid": "uuid",
  "playerName": "name",
  "dimension": "minecraft:overworld",
  "position": {
    "x": 120.5,
    "y": 64.0,
    "z": -430.25
  },
  "occurredAt": "2026-05-10T18:00:00Z"
}
```

`occurredAt` debe ser UTC en formato ISO-8601.

## Eventos y datos

### player_connected

Emitir cuando un `ServerPlayer` entra al servidor.

Payload:

```json
{
  "ip": "opcional-si-esta-disponible",
  "gameMode": "survival",
  "health": 20.0,
  "food": 20,
  "xpLevel": 0
}
```

Despues de conectar, enviar tambien un `inventory_snapshot`.

### player_disconnected

Emitir cuando el jugador sale.

Payload:

```json
{
  "reason": "opcional",
  "gameMode": "survival",
  "health": 18.0,
  "food": 16,
  "xpLevel": 12
}
```

### player_death

Emitir desde evento de muerte de entidad, filtrando solo `ServerPlayer`.

Payload:

```json
{
  "deathMessage": "Player was slain by Zombie",
  "damageType": "minecraft:mob_attack",
  "killerName": "Zombie",
  "killerType": "minecraft:zombie",
  "healthBeforeDeath": 0.0,
  "xpLevel": 18
}
```

Despues de la muerte, enviar tambien un `inventory_snapshot` si el inventario sigue disponible.

### player_damaged

Emitir cuando un `ServerPlayer` recibe dano.

Payload:

```json
{
  "amount": 4.0,
  "damageType": "minecraft:fall",
  "attackerName": "",
  "attackerType": "",
  "healthBefore": 20.0,
  "healthAfterEstimated": 16.0,
  "armor": 8,
  "absorption": 0.0
}
```

No spamear este evento si llega demasiado frecuente. Aplicar rate limit por jugador, por ejemplo maximo 10 eventos por segundo por jugador. Muertes siempre se envian.

### dimension_changed

Emitir cuando el jugador cambia de dimension.

Payload:

```json
{
  "from": "minecraft:overworld",
  "to": "minecraft:the_nether"
}
```

### inventory_snapshot

Emitir:

- Al conectar.
- Al morir.
- Cada 60 segundos por jugador online, si `inventorySnapshotsEnabled=true`.
- Al desconectar, si es barato hacerlo.

Payload:

```json
{
  "items": [
    {
      "slot": 0,
      "item": "minecraft:diamond_sword",
      "count": 1,
      "damage": 23,
      "maxDamage": 1561,
      "customName": "opcional",
      "enchantments": [
        {
          "id": "minecraft:sharpness",
          "level": 5
        }
      ]
    }
  ],
  "selectedSlot": 0,
  "armor": [],
  "offhand": []
}
```

No enviar NBT completo. Puede ser grande y puede contener datos privados de otros mods. Enviar solo resumen.

### advancement_completed

Emitir cuando el jugador completa un advancement.

Payload:

```json
{
  "advancementId": "minecraft:story/mine_stone",
  "title": "Stone Age"
}
```

## Cliente HTTP

Usar `java.net.http.HttpClient`, disponible en Java 17.

Reglas:

- Enviar con `sendAsync`, nunca bloquear el hilo principal del servidor.
- Timeout recomendado: 3 segundos.
- Si el API falla, loguear warning resumido.
- No reintentar infinitamente.
- Mantener una cola pequena en memoria si se quiere tolerar caidas temporales, por ejemplo 500 eventos maximo.
- Si la cola se llena, descartar eventos nuevos de baja prioridad (`player_damaged`, `inventory_snapshot`) antes que muertes/conexiones.

Prioridad de eventos:

```txt
alta: player_death, player_connected, player_disconnected
media: dimension_changed, advancement_completed
baja: player_damaged, inventory_snapshot
```

## Seguridad

- Nunca hardcodear `apiKey` en el jar.
- Nunca usar `ADMIN_API_KEY`.
- No imprimir la API key en logs.
- No enviar chat privado, comandos, NBT completo ni IP si no hace falta.
- El mod debe funcionar aunque el API este caido.

## Forma del JSON final

Ejemplo completo para muerte:

```json
{
  "serverId": "survival-01",
  "serverName": "Survival 01",
  "packId": "survival",
  "packVersion": "1.0.0",
  "minecraftVersion": "1.20.1",
  "loader": "forge",
  "eventType": "player_death",
  "playerUuid": "00000000-0000-0000-0000-000000000000",
  "playerName": "Player",
  "dimension": "minecraft:overworld",
  "position": {
    "x": 120.5,
    "y": 64.0,
    "z": -430.25
  },
  "occurredAt": "2026-05-10T18:00:00Z",
  "payload": {
    "deathMessage": "Player was slain by Zombie",
    "damageType": "minecraft:mob_attack",
    "killerType": "minecraft:zombie",
    "killerName": "Zombie",
    "xpLevel": 18
  }
}
```

## Clases recomendadas

```txt
com.iruburu.telemetry.IruburuTelemetryMod
com.iruburu.telemetry.TelemetryConfig
com.iruburu.telemetry.TelemetryClient
com.iruburu.telemetry.TelemetryEvent
com.iruburu.telemetry.TelemetryEvents
com.iruburu.telemetry.PlayerSnapshot
com.iruburu.telemetry.InventorySnapshot
```

Responsabilidades:

```txt
IruburuTelemetryMod: registrar listeners de Forge.
TelemetryConfig: leer properties/config y validar si telemetria esta habilitada.
TelemetryClient: serializar y enviar HTTP async.
TelemetryEvent: DTO del request al API.
TelemetryEvents: handlers para Forge events.
PlayerSnapshot: helpers para dimension, posicion, vida, comida, XP.
InventorySnapshot: convertir inventario a JSON pequeno.
```

## Criterios de terminado

El mod esta listo cuando:

- Compila con Forge 1.20.1 / Java 17.
- Arranca en dedicated server sin cliente.
- Si falta `apiKey`, no crashea.
- Reporta login/logout.
- Reporta muerte con causa, posicion y dimension.
- Reporta dano con rate limit.
- Reporta cambio de dimension.
- Reporta advancement.
- Reporta inventario resumido.
- Ningun envio HTTP bloquea el thread principal.
- El API responde `202 Accepted` y devuelve `id`.
