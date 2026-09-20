package ru.rooyzee.elytrixclans.function.impl.invite;

public class Invite {

    private String inviter;
    private String clanName;

    public Invite(String inviter, String clanName) {
        this.inviter = inviter;
        this.clanName = clanName;
    }

    public String getInviter() { return inviter; }
    public String getClanName() { return clanName; }
}