package org.opennms.plugins.servicenow.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.NONE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NetworkDevice {

    @JsonProperty("Model_ID")
    private String modelId;

    @JsonProperty("u_categoria")
    private String categoria;

    @JsonProperty("sys_class_name")
    private String sysClassName;

    @JsonProperty("asset_tag")
    private String assetTag;

    @JsonProperty("name")
    private String name;

    @JsonProperty("u_marca")
    private String marca;

    @JsonProperty("u_modello")
    private String modello;

    @JsonProperty("location")
    private String location;

    @JsonProperty("u_latitudine")
    private String latitudine;

    @JsonProperty("u_longitudine")
    private String longitudine;

    @JsonProperty("ip_address")
    private String ipAddress;

    @JsonProperty("u_parental_node")
    private String parentalNode;

    @JsonProperty("install_status")
    private int installStatus;

    @JsonProperty("u_tipo_apparato")
    private String tipoApparato;

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getSysClassName() {
        return sysClassName;
    }

    public void setSysClassName(String sysClassName) {
        this.sysClassName = sysClassName;
    }

    public String getAssetTag() {
        return assetTag;
    }

    public void setAssetTag(String assetTag) {
        this.assetTag = assetTag;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMarca() {
        return marca;
    }

    public void setMarca(String marca) {
        this.marca = marca;
    }

    public String getModello() {
        return modello;
    }

    public void setModello(String modello) {
        this.modello = modello;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getLatitudine() {
        return latitudine;
    }

    public void setLatitudine(String latitudine) {
        this.latitudine = latitudine;
    }

    public String getLongitudine() {
        return longitudine;
    }

    public void setLongitudine(String longitudine) {
        this.longitudine = longitudine;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getParentalNode() {
        return parentalNode;
    }

    public void setParentalNode(String parentalNode) {
        this.parentalNode = parentalNode;
    }

    public int getInstallStatus() {
        return installStatus;
    }

    public void setInstallStatus(int installStatus) {
        this.installStatus = installStatus;
    }

    public String getTipoApparato() {
        return tipoApparato;
    }

    public void setTipoApparato(String tipoApparato) {
        this.tipoApparato = tipoApparato;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NetworkDevice that = (NetworkDevice) o;
        return Objects.equals(assetTag, that.assetTag) &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assetTag, name);
    }

    @Override
    public String toString() {
        return "NetworkDevice{" +
                "modelId='" + modelId + '\'' +
                ", categoria='" + categoria + '\'' +
                ", sysClassName='" + sysClassName + '\'' +
                ", assetTag='" + assetTag + '\'' +
                ", name='" + name + '\'' +
                ", marca='" + marca + '\'' +
                ", modello='" + modello + '\'' +
                ", location='" + location + '\'' +
                ", latitudine='" + latitudine + '\'' +
                ", longitudine='" + longitudine + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", parentalNode='" + parentalNode + '\'' +
                ", installStatus=" + installStatus +
                ", tipoApparato='" + tipoApparato + '\'' +
                '}';
    }

}