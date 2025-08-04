/*
 * This file is part of packetevents - https://github.com/retrooper/packetevents
 * Copyright (C) 2022 retrooper and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.retrooper.packetevents.wrapper.play.server;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.attribute.Attribute;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;

import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WrapperPlayServerUpdateAttributes extends PacketWrapper {

    private static final List<Map.Entry<String, Attribute>> PRE_1_16_ATTRIBUTES = Collections.unmodifiableList(Arrays.asList(
            new SimpleEntry<>("generic.maxHealth", Attributes.MAX_HEALTH),
            new SimpleEntry<>("Max Health", Attributes.MAX_HEALTH),
            new SimpleEntry<>("zombie.spawnReinforcements", Attributes.SPAWN_REINFORCEMENTS),
            new SimpleEntry<>("Spawn Reinforcements Chance", Attributes.SPAWN_REINFORCEMENTS),
            new SimpleEntry<>("horse.jumpStrength", Attributes.HORSE_JUMP_STRENGTH),
            new SimpleEntry<>("Jump Strength", Attributes.HORSE_JUMP_STRENGTH),
            new SimpleEntry<>("generic.followRange", Attributes.FOLLOW_RANGE),
            new SimpleEntry<>("Follow Range", Attributes.FOLLOW_RANGE),
            new SimpleEntry<>("generic.knockbackResistance", Attributes.KNOCKBACK_RESISTANCE),
            new SimpleEntry<>("Knockback Resistance", Attributes.KNOCKBACK_RESISTANCE),
            new SimpleEntry<>("generic.movementSpeed", Attributes.MOVEMENT_SPEED),
            new SimpleEntry<>("Movement Speed", Attributes.MOVEMENT_SPEED),
            new SimpleEntry<>("generic.flyingSpeed", Attributes.FLYING_SPEED),
            new SimpleEntry<>("Flying Speed", Attributes.FLYING_SPEED),
            new SimpleEntry<>("generic.attackDamage", Attributes.ATTACK_DAMAGE),
            new SimpleEntry<>("generic.attackKnockback", Attributes.ATTACK_KNOCKBACK),
            new SimpleEntry<>("generic.attackSpeed", Attributes.ATTACK_SPEED),
            new SimpleEntry<>("generic.armorToughness", Attributes.ARMOR_TOUGHNESS),
            new SimpleEntry<>("generic.armor", Attributes.ARMOR),
            new SimpleEntry<>("generic.luck", Attributes.LUCK)
    ));
    private static final Map<String, Attribute> PRE_1_16_ATTRIBUTES_MAP = PRE_1_16_ATTRIBUTES.stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    private static final Map<Attribute, String> PRE_1_16_ATTRIBUTES_RMAP = PRE_1_16_ATTRIBUTES.stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey,
                    (s1, s2) -> s1));

    private int entityID;
    private List<Property> properties;

    public WrapperPlayServerUpdateAttributes(PacketSendEvent event) {
        super(event);
    }

    public WrapperPlayServerUpdateAttributes(int entityID, List<Property> properties) {
        super(PacketType.Play.Server.UPDATE_ATTRIBUTES);
        this.entityID = entityID;
        this.properties = properties;
    }

    @Override
    public void read() {
        try {
            if (this.serverVersion.isOlderThanOrEquals(ServerVersion.V_1_7_10)) {
                if (!canReadBytes(4)) return;
                entityID = readInt();
            } else {
                if (!canReadBytes(1)) return;
                entityID = readVarInt();
            }

            int propertyCount;
            if (serverVersion.isNewerThanOrEquals(ServerVersion.V_1_17)) {
                if (!canReadBytes(1)) return;
                propertyCount = readVarInt();
            } else {
                if (!canReadBytes(4)) return;
                propertyCount = readInt();
            }
            properties = new ArrayList<>(propertyCount);

            for (int i = 0; i < propertyCount; i++) {
                Attribute attribute;

                if (this.serverVersion.isNewerThanOrEquals(ServerVersion.V_1_20_5)) {
                    attribute = this.readMappedEntity(Attributes::getById);
                } else if (this.serverVersion.isNewerThanOrEquals(ServerVersion.V_1_16)) {
                    if (!canReadIdentifier()) return;
                    attribute = Attributes.getByName(this.readIdentifier().toString());
                } else {
                    if (!canReadString(64)) return;
                    String attributeName = this.readString(64);
                    attribute = PRE_1_16_ATTRIBUTES_MAP.get(attributeName);
                    if (attribute == null) {
                        attribute = Attributes.getByName(attributeName);
                    }
                    if (attribute == null) {
                        System.err.println("Can't find attribute for name " + attributeName
                                + " (version: " + this.serverVersion.name() + ")");
                        return;
                    }
                }

                if (!canReadBytes(8)) return;
                double value = readDouble();

                int modifiersLength;
                if (this.serverVersion.isOlderThanOrEquals(ServerVersion.V_1_7_10)) {
                    if (!canReadBytes(2)) return;
                    modifiersLength = readShort();
                } else {
                    if (!canReadBytes(1)) return;
                    modifiersLength = readVarInt();
                }

                List<PropertyModifier> modifiers = new ArrayList<>(modifiersLength);
                for (int j = 0; j < modifiersLength; j++) {
                    ResourceLocation name;
                    UUID uuid;

                    if (this.serverVersion.isNewerThanOrEquals(ServerVersion.V_1_21)) {
                        if (!canReadIdentifier()) return;
                        name = this.readIdentifier();
                        uuid = PropertyModifier.generateSemiUniqueId(name);
                    } else {
                        if (!canReadBytes(16)) return;
                        uuid = this.readUUID();
                        name = new ResourceLocation(uuid.toString());
                    }

                    if (!canReadBytes(8)) return;
                    double amount = readDouble();

                    if (!canReadBytes(1)) return;
                    byte operationIndex = readByte();

                    if (operationIndex >= PropertyModifier.Operation.VALUES.length || operationIndex < 0) {
                        System.err.println("Operation index out of bounds: " + operationIndex);
                        return;
                    }

                    PropertyModifier.Operation operation = PropertyModifier.Operation.VALUES[operationIndex];
                    modifiers.add(new PropertyModifier(name, uuid, amount, operation));
                }
                this.properties.add(new Property(attribute, value, modifiers));
            }
        } catch (Exception e) {
            System.err.println("Error reading WrapperPlayServerUpdateAttributes packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void write() {
        if (this.serverVersion.isOlderThanOrEquals(ServerVersion.V_1_7_10)) {
            writeInt(entityID);
        } else {
            writeVarInt(entityID);
        }

        if (serverVersion.isNewerThanOrEquals(ServerVersion.V_1_17)) {
            writeVarInt(properties.size());
        } else {
            writeInt(properties.size());
        }
        for (Property property : properties) {
            if (this.serverVersion.isNewerThanOrEquals(ServerVersion.V_1_20_5)) {
                this.writeVarInt(property.getAttribute().getId(this.serverVersion.toClientVersion()));
            } else if (this.serverVersion.isNewerThanOrEquals(ServerVersion.V_1_16)) {
                this.writeIdentifier(property.getAttribute().getName(this.serverVersion.toClientVersion()));
            } else {
                String str = PRE_1_16_ATTRIBUTES_RMAP.get(property.getAttribute());
                this.writeString(str != null ? str : property.getAttribute().getName().toString());
            }

            writeDouble(property.value);
            if (this.serverVersion.isOlderThanOrEquals(ServerVersion.V_1_7_10)) {
                writeShort(property.modifiers.size());
            } else {
                writeVarInt(property.modifiers.size());
            }
            for (PropertyModifier modifier : property.modifiers) {
                if (this.serverVersion.isNewerThanOrEquals(ServerVersion.V_1_21)) {
                    this.writeIdentifier(modifier.name);
                } else {
                    this.writeUUID(modifier.uuid);
                }
                writeDouble(modifier.amount);
                writeByte(modifier.operation.ordinal());
            }
        }
    }

    @Override
    public void copy(WrapperPlayServerUpdateAttributes wrapper) {
        entityID = wrapper.entityID;
        properties = wrapper.properties;
    }

    public int getEntityId() {
        return entityID;
    }

    public void setEntityId(int entityID) {
        this.entityID = entityID;
    }

    public List<Property> getProperties() {
        return properties;
    }

    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }

    private boolean canReadBytes(int bytes) {
        return readableBytes() >= bytes;
    }

    private boolean canReadString(int maxLength) {
        if (!canReadBytes(1)) return false;
        int length = peekStringLength();
        return canReadBytes(length);
    }

    private boolean canReadIdentifier() {
        return canReadBytes(3);
    }

    private int peekStringLength() {
        return 64;
    }

    public static class PropertyModifier {
        public enum Operation {
            ADDITION,
            MULTIPLY_BASE,
            MULTIPLY_TOTAL;

            public static final Operation[] VALUES = values();
        }

        private ResourceLocation name;
        private UUID uuid;
        private double amount;
        private Operation operation;

        public PropertyModifier(UUID uuid, double amount, Operation operation) {
            this(new ResourceLocation(uuid.toString()), uuid, amount, operation);
        }

        public PropertyModifier(ResourceLocation name, double amount, Operation operation) {
            this(name, generateSemiUniqueId(name), amount, operation);
        }

        public PropertyModifier(ResourceLocation name, UUID uuid, double amount, Operation operation) {
            this.name = name;
            this.uuid = uuid;
            this.amount = amount;
            this.operation = operation;
        }

        public static UUID generateSemiUniqueId(ResourceLocation name) {
            String extendedName = "packetevents_" + name.toString();
            return UUID.nameUUIDFromBytes(extendedName.getBytes(StandardCharsets.UTF_8));
        }

        public ResourceLocation getName() {
            return this.name;
        }

        public void setName(ResourceLocation name) {
            this.name = name;
            this.uuid = generateSemiUniqueId(name);
        }

        public UUID getUUID() {
            return uuid;
        }

        public void setUUID(UUID uuid) {
            this.name = new ResourceLocation(uuid.toString());
            this.uuid = uuid;
        }

        public void setNameAndUUID(ResourceLocation name, UUID uuid) {
            this.name = name;
            this.uuid = uuid;
        }

        public double getAmount() {
            return amount;
        }

        public void setAmount(double amount) {
            this.amount = amount;
        }

        public Operation getOperation() {
            return operation;
        }

        public void setOperation(Operation operation) {
            this.operation = operation;
        }
    }

    public static class Property {

        private Attribute attribute;
        private double value;
        private List<PropertyModifier> modifiers;
        private transient Double calculatedValue = null;

        @Deprecated
        public Property(String key, double value, List<PropertyModifier> modifiers) {
            this(Attributes.getByName(key), value, modifiers);
        }

        public Property(Attribute attribute, double value, List<PropertyModifier> modifiers) {
            this.attribute = attribute;
            this.value = value;
            this.modifiers = modifiers;
        }

        public double calcValue() {
            if (this.calculatedValue == null) {
                this.calculatedValue = this.calcValue0();
            }
            return this.calculatedValue;
        }

        public double calcValue0() {
            double base = this.getValue();
            for (PropertyModifier modifier : this.modifiers) {
                if (modifier.getOperation() == PropertyModifier.Operation.ADDITION) {
                    base += modifier.getAmount();
                }
            }
            double value = base;
            for (PropertyModifier modifier : this.modifiers) {
                if (modifier.getOperation() == PropertyModifier.Operation.MULTIPLY_BASE) {
                    value += base * modifier.getAmount();
                }
            }
            for (PropertyModifier modifier : this.modifiers) {
                if (modifier.getOperation() == PropertyModifier.Operation.MULTIPLY_TOTAL) {
                    value *= 1d + modifier.getAmount();
                }
            }
            return this.attribute.sanitizeValue(value);
        }

        public Attribute getAttribute() {
            return this.attribute;
        }

        public void setAttribute(Attribute attribute) {
            this.attribute = attribute;
        }

        @Deprecated
        public String getKey() {
            return this.getAttribute().getName().toString();
        }

        @Deprecated
        public void setKey(String key) {
            this.setAttribute(Attributes.getByName(key));
        }

        public double getValue() {
            return value;
        }

        public void setValue(double value) {
            this.value = value;
            this.setDirty();
        }

        public void addModifier(PropertyModifier modifier) {
            this.modifiers.add(modifier);
            this.setDirty();
        }

        public boolean removeModifier(ResourceLocation modifierId) {
            return this.removeModifierIf(modifier -> modifierId.equals(modifier.getName()));
        }

        @Deprecated
        public boolean removeModifier(UUID modifierId) {
            return this.removeModifierIf(modifier -> modifierId.equals(modifier.getUUID()));
        }

        @Deprecated
        public boolean removeModifier(ResourceLocation modifierId, UUID modifierUId) {
            return this.removeModifierIf(modifier -> modifierUId.equals(modifier.getUUID())
                    || modifierId.equals(modifier.getName()));
        }

        public boolean removeModifierIf(Predicate<PropertyModifier> predicate) {
            boolean ret = this.modifiers.removeIf(predicate);
            if (ret) this.setDirty();
            return ret;
        }

        public List<PropertyModifier> getModifiers() {
            return modifiers;
        }

        public void setModifiers(List<PropertyModifier> modifiers) {
            this.modifiers = modifiers;
            this.setDirty();
        }

        public void setDirty() {
            this.calculatedValue = null;
        }
    }
}
